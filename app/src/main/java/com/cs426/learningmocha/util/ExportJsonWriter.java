package com.cs426.learningmocha.util;

import com.cs426.learningmocha.backup.BackupSnapshot;
import com.cs426.learningmocha.data.local.entity.DictionaryEntry;
import com.cs426.learningmocha.data.local.entity.Link;
import com.cs426.learningmocha.data.local.entity.Node;
import com.cs426.learningmocha.data.local.entity.PostTag;
import com.cs426.learningmocha.data.local.entity.ResourceItem;
import com.cs426.learningmocha.data.local.entity.Tag;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.io.Writer;

/**
 * Streams a {@link BackupSnapshot} to a versioned {@code .mocha.json} envelope.
 * Framework-free so the round trip can be unit-tested on the JVM.
 */
public final class ExportJsonWriter {

    private ExportJsonWriter() {}

    public static void write(Writer out, BackupSnapshot data, long exportedAt) throws IOException {
        JsonWriter json = new JsonWriter(out);
        json.setIndent("  ");
        json.beginObject();
        json.name("format").value(BackupSnapshot.FORMAT);
        json.name("version").value(BackupSnapshot.VERSION);
        json.name("exportedAt").value(exportedAt);

        json.name("nodes").beginArray();
        for (Node n : data.getNodes()) {
            json.beginObject();
            json.name("id").value(n.getId());
            json.name("parentId").value(n.getParentId());
            json.name("type").value(n.getType().name());
            json.name("title").value(n.getTitle());
            json.name("content").value(n.getContent());
            json.name("status").value(n.getStatus().name());
            json.name("favorite").value(n.getFavorite());
            json.name("orderIndex").value(n.getOrderIndex());
            json.name("createdAt").value(n.getCreatedAt());
            json.name("updatedAt").value(n.getUpdatedAt());
            json.name("icon").value(n.getIcon());
            json.name("color").value(n.getColor());
            json.name("nextPostId").value(n.getNextPostId());
            json.endObject();
        }
        json.endArray();

        json.name("links").beginArray();
        for (Link l : data.getLinks()) {
            json.beginObject();
            json.name("fromPostId").value(l.getFromPostId());
            json.name("toPostId").value(l.getToPostId());
            json.name("anchorText").value(l.getAnchorText());
            json.endObject();
        }
        json.endArray();

        json.name("tags").beginArray();
        for (Tag t : data.getTags()) {
            json.beginObject();
            json.name("id").value(t.getId());
            json.name("name").value(t.getName());
            json.endObject();
        }
        json.endArray();

        json.name("postTags").beginArray();
        for (PostTag pt : data.getPostTags()) {
            json.beginObject();
            json.name("postId").value(pt.getPostId());
            json.name("tagId").value(pt.getTagId());
            json.endObject();
        }
        json.endArray();

        json.name("dictionary").beginArray();
        for (DictionaryEntry d : data.getDictionary()) {
            json.beginObject();
            json.name("postId").value(d.getPostId());
            json.name("term").value(d.getTerm());
            json.name("definition").value(d.getDefinition());
            json.name("meaningVi").value(d.getMeaningVi());
            json.endObject();
        }
        json.endArray();

        json.name("resources").beginArray();
        for (ResourceItem r : data.getResources()) {
            json.beginObject();
            json.name("postId").value(r.getPostId());
            json.name("type").value(r.getType().name());
            json.name("title").value(r.getTitle());
            json.name("url").value(r.getUrl());
            json.endObject();
        }
        json.endArray();

        json.endObject();
        json.flush();
    }
}
