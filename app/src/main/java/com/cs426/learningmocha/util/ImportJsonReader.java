package com.cs426.learningmocha.util;

import com.cs426.learningmocha.backup.BackupSnapshot;
import com.cs426.learningmocha.data.local.entity.DictionaryEntry;
import com.cs426.learningmocha.data.local.entity.LearningStatus;
import com.cs426.learningmocha.data.local.entity.Link;
import com.cs426.learningmocha.data.local.entity.Node;
import com.cs426.learningmocha.data.local.entity.NodeType;
import com.cs426.learningmocha.data.local.entity.PostTag;
import com.cs426.learningmocha.data.local.entity.ResourceItem;
import com.cs426.learningmocha.data.local.entity.ResourceType;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses a {@code .mocha.json} envelope written by {@link ExportJsonWriter}.
 * Unknown fields are skipped so a file from a newer minor build still imports.
 * Framework-free so the round trip can be unit-tested on the JVM.
 */
public final class ImportJsonReader {

    private ImportJsonReader() {}

    /** Thrown when the file is not a Learning Mocha backup we understand. */
    public static class InvalidBackupException extends IOException {
        public InvalidBackupException(String message) {
            super(message);
        }
    }

    public static BackupSnapshot read(Reader in) throws IOException {
        List<Node> nodes = new ArrayList<>();
        List<Link> links = new ArrayList<>();
        List<com.cs426.learningmocha.data.local.entity.Tag> tags = new ArrayList<>();
        List<PostTag> postTags = new ArrayList<>();
        List<DictionaryEntry> dictionary = new ArrayList<>();
        List<ResourceItem> resources = new ArrayList<>();

        String format = null;
        int version = 0;

        JsonReader json = new JsonReader(in);
        if (json.peek() != JsonToken.BEGIN_OBJECT) {
            throw new InvalidBackupException("Not a Learning Mocha backup file");
        }
        json.beginObject();
        while (json.hasNext()) {
            String field = json.nextName();
            switch (field) {
                case "format":
                    format = json.nextString();
                    break;
                case "version":
                    version = json.nextInt();
                    break;
                case "nodes":
                    readArray(json, nodes, ImportJsonReader::readNode);
                    break;
                case "links":
                    readArray(json, links, ImportJsonReader::readLink);
                    break;
                case "tags":
                    readArray(json, tags, ImportJsonReader::readTag);
                    break;
                case "postTags":
                    readArray(json, postTags, ImportJsonReader::readPostTag);
                    break;
                case "dictionary":
                    readArray(json, dictionary, ImportJsonReader::readEntry);
                    break;
                case "resources":
                    readArray(json, resources, ImportJsonReader::readResource);
                    break;
                default:
                    json.skipValue();
            }
        }
        json.endObject();

        if (!BackupSnapshot.FORMAT.equals(format)) {
            throw new InvalidBackupException("Not a Learning Mocha backup file");
        }
        if (version > BackupSnapshot.VERSION) {
            throw new InvalidBackupException(
                    "This backup was written by a newer version of the app");
        }
        return new BackupSnapshot(nodes, links, tags, postTags, dictionary, resources);
    }

    private interface RowReader<T> {
        T read(JsonReader json) throws IOException;
    }

    private static <T> void readArray(JsonReader json, List<T> into, RowReader<T> row)
            throws IOException {
        if (json.peek() == JsonToken.NULL) {
            json.skipValue();
            return;
        }
        json.beginArray();
        while (json.hasNext()) {
            into.add(row.read(json));
        }
        json.endArray();
    }

    private static Node readNode(JsonReader json) throws IOException {
        long id = 0;
        Long parentId = null;
        String type = NodeType.POST.name();
        String title = "";
        String content = null;
        String status = LearningStatus.NONE.name();
        boolean favorite = false;
        int orderIndex = 0;
        long createdAt = 0;
        long updatedAt = 0;

        json.beginObject();
        while (json.hasNext()) {
            String field = json.nextName();
            switch (field) {
                case "id": id = json.nextLong(); break;
                case "parentId": parentId = nextNullableLong(json); break;
                case "type": type = json.nextString(); break;
                case "title": title = nextNullableString(json); break;
                case "content": content = nextNullableString(json); break;
                case "status": status = json.nextString(); break;
                case "favorite": favorite = json.nextBoolean(); break;
                case "orderIndex": orderIndex = json.nextInt(); break;
                case "createdAt": createdAt = json.nextLong(); break;
                case "updatedAt": updatedAt = json.nextLong(); break;
                default: json.skipValue();
            }
        }
        json.endObject();

        if (title == null || title.trim().isEmpty()) {
            throw new InvalidBackupException("A node in the backup has no title");
        }
        return new Node(
                id,
                parentId,
                parseEnum(NodeType.class, type, NodeType.POST),
                title,
                content,
                parseEnum(LearningStatus.class, status, LearningStatus.NONE),
                favorite,
                orderIndex,
                createdAt,
                updatedAt);
    }

    private static Link readLink(JsonReader json) throws IOException {
        long from = 0;
        long to = 0;
        String anchor = "";
        json.beginObject();
        while (json.hasNext()) {
            String field = json.nextName();
            switch (field) {
                case "fromPostId": from = json.nextLong(); break;
                case "toPostId": to = json.nextLong(); break;
                case "anchorText": anchor = nextNullableString(json); break;
                default: json.skipValue();
            }
        }
        json.endObject();
        return new Link(0L, from, to, anchor == null ? "" : anchor);
    }

    private static com.cs426.learningmocha.data.local.entity.Tag readTag(JsonReader json)
            throws IOException {
        long id = 0;
        String name = "";
        json.beginObject();
        while (json.hasNext()) {
            String field = json.nextName();
            switch (field) {
                case "id": id = json.nextLong(); break;
                case "name": name = nextNullableString(json); break;
                default: json.skipValue();
            }
        }
        json.endObject();
        return new com.cs426.learningmocha.data.local.entity.Tag(id, name == null ? "" : name);
    }

    private static PostTag readPostTag(JsonReader json) throws IOException {
        long postId = 0;
        long tagId = 0;
        json.beginObject();
        while (json.hasNext()) {
            String field = json.nextName();
            switch (field) {
                case "postId": postId = json.nextLong(); break;
                case "tagId": tagId = json.nextLong(); break;
                default: json.skipValue();
            }
        }
        json.endObject();
        return new PostTag(postId, tagId);
    }

    private static DictionaryEntry readEntry(JsonReader json) throws IOException {
        Long postId = null;
        String term = "";
        String definition = "";
        String meaningVi = "";
        json.beginObject();
        while (json.hasNext()) {
            String field = json.nextName();
            switch (field) {
                case "postId": postId = nextNullableLong(json); break;
                case "term": term = nextNullableString(json); break;
                case "definition": definition = nextNullableString(json); break;
                case "meaningVi": meaningVi = nextNullableString(json); break;
                default: json.skipValue();
            }
        }
        json.endObject();
        return new DictionaryEntry(
                0L,
                postId,
                term == null ? "" : term,
                definition == null ? "" : definition,
                meaningVi == null ? "" : meaningVi);
    }

    private static ResourceItem readResource(JsonReader json) throws IOException {
        long postId = 0;
        String type = ResourceType.OTHER.name();
        String title = "";
        String url = "";
        json.beginObject();
        while (json.hasNext()) {
            String field = json.nextName();
            switch (field) {
                case "postId": postId = json.nextLong(); break;
                case "type": type = json.nextString(); break;
                case "title": title = nextNullableString(json); break;
                case "url": url = nextNullableString(json); break;
                default: json.skipValue();
            }
        }
        json.endObject();
        return new ResourceItem(
                0L,
                postId,
                parseEnum(ResourceType.class, type, ResourceType.OTHER),
                title == null ? "" : title,
                url == null ? "" : url);
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> type, String raw, E fallback) {
        if (raw == null) {
            return fallback;
        }
        try {
            return Enum.valueOf(type, raw.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }

    private static Long nextNullableLong(JsonReader json) throws IOException {
        if (json.peek() == JsonToken.NULL) {
            json.nextNull();
            return null;
        }
        return json.nextLong();
    }

    private static String nextNullableString(JsonReader json) throws IOException {
        if (json.peek() == JsonToken.NULL) {
            json.nextNull();
            return null;
        }
        return json.nextString();
    }
}
