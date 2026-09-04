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
import com.cs426.learningmocha.data.local.entity.Tag;

import org.junit.Test;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class BackupRoundTripTest {

    private static BackupSnapshot sample() {
        Node branch = new Node(
                1L, null, NodeType.BRANCH, "Distributed Systems", null,
                LearningStatus.NONE, false, 0, 100L, 200L);
        Node post = new Node(
                2L, 1L, NodeType.POST, "Raft", "# Raft\nSee [[Paxos]].",
                LearningStatus.FINISHED, true, 3, 300L, 400L);
        return new BackupSnapshot(
                Arrays.asList(branch, post),
                Collections.singletonList(new Link(7L, 2L, 2L, "Paxos")),
                Collections.singletonList(new Tag(5L, "consensus")),
                Collections.singletonList(new PostTag(2L, 5L)),
                Arrays.asList(
                        new DictionaryEntry(9L, 2L, "quorum", "A majority", "đa số"),
                        new DictionaryEntry(10L, null, "global term", "Not tied to a post", "")),
                Collections.singletonList(
                        new ResourceItem(11L, 2L, ResourceType.YOUTUBE, "Raft talk", "https://y.t/1")));
    }

    private static BackupSnapshot roundTrip(BackupSnapshot in) throws IOException {
        StringWriter out = new StringWriter();
        ExportJsonWriter.write(out, in, 1234L);
        return ImportJsonReader.read(new StringReader(out.toString()));
    }

    @Test
    public void preservesEveryTableThroughARoundTrip() throws IOException {
        BackupSnapshot back = roundTrip(sample());

        assertEquals(2, back.getNodes().size());
        assertEquals(1, back.getLinks().size());
        assertEquals(1, back.getTags().size());
        assertEquals(1, back.getPostTags().size());
        assertEquals(2, back.getDictionary().size());
        assertEquals(1, back.getResources().size());
        assertEquals(1, back.getPostCount());
    }

    @Test
    public void preservesPostFieldsIncludingMarkdownAndUnicode() throws IOException {
        Node post = null;
        for (Node n : roundTrip(sample()).getNodes()) {
            if (n.getType() == NodeType.POST) {
                post = n;
            }
        }
        assertNotNull(post);
        assertEquals("Raft", post.getTitle());
        assertEquals("# Raft\nSee [[Paxos]].", post.getContent());
        assertEquals(LearningStatus.FINISHED, post.getStatus());
        assertTrue(post.getFavorite());
        assertEquals(3, post.getOrderIndex());
        assertEquals(Long.valueOf(1L), post.getParentId());
        assertEquals(300L, post.getCreatedAt());
        assertEquals(400L, post.getUpdatedAt());

        assertEquals("đa số", roundTrip(sample()).getDictionary().get(0).getMeaningVi());
    }

    /** Node ids survive so the repository can rewrite foreign keys against them. */
    @Test
    public void keepsNodeIdentityForForeignKeyRemapping() throws IOException {
        BackupSnapshot back = roundTrip(sample());
        assertEquals(1L, back.getNodes().get(0).getId());
        assertEquals(2L, back.getNodes().get(1).getId());
        assertEquals(5L, back.getTags().get(0).getId());
        assertEquals(2L, back.getPostTags().get(0).getPostId());
        assertEquals(5L, back.getPostTags().get(0).getTagId());
    }

    @Test
    public void keepsGlobalDictionaryTermsGlobal() throws IOException {
        BackupSnapshot back = roundTrip(sample());
        assertEquals(Long.valueOf(2L), back.getDictionary().get(0).getPostId());
        assertNull(back.getDictionary().get(1).getPostId());
    }

    @Test
    public void emptyLibraryRoundTripsToAnEmptySnapshot() throws IOException {
        BackupSnapshot back = roundTrip(new BackupSnapshot(
                Collections.<Node>emptyList(),
                Collections.<Link>emptyList(),
                Collections.<Tag>emptyList(),
                Collections.<PostTag>emptyList(),
                Collections.<DictionaryEntry>emptyList(),
                Collections.<ResourceItem>emptyList()));
        assertTrue(back.isEmpty());
        assertTrue(back.getNodes().isEmpty());
    }

    @Test
    public void writesAVersionedEnvelope() throws IOException {
        StringWriter out = new StringWriter();
        ExportJsonWriter.write(out, sample(), 4242L);
        String json = out.toString();
        assertTrue(json.contains("\"format\": \"mocha.backup\""));
        assertTrue(json.contains("\"version\": 1"));
        assertTrue(json.contains("\"exportedAt\": 4242"));
    }

    @Test
    public void rejectsAFileThatIsNotABackup() {
        try {
            ImportJsonReader.read(new StringReader("{\"hello\":\"world\"}"));
            fail("expected InvalidBackupException");
        } catch (IOException expected) {
            assertTrue(expected instanceof ImportJsonReader.InvalidBackupException);
        }
    }

    @Test
    public void rejectsNonObjectJson() {
        try {
            ImportJsonReader.read(new StringReader("[1,2,3]"));
            fail("expected InvalidBackupException");
        } catch (IOException expected) {
            assertTrue(expected instanceof ImportJsonReader.InvalidBackupException);
        }
    }

    /** Forward compatibility: refuse a newer format rather than silently dropping data. */
    @Test
    public void rejectsANewerBackupVersion() {
        String json = "{\"format\":\"mocha.backup\",\"version\":99,\"nodes\":[]}";
        try {
            ImportJsonReader.read(new StringReader(json));
            fail("expected InvalidBackupException");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("newer version"));
        }
    }

    /** A file from a future minor build may carry fields this build has never heard of. */
    @Test
    public void skipsUnknownFieldsAndSections() throws IOException {
        String json = "{\"format\":\"mocha.backup\",\"version\":1,"
                + "\"somethingNew\":{\"a\":[1,2]},"
                + "\"nodes\":[{\"id\":1,\"parentId\":null,\"type\":\"POST\",\"title\":\"Raft\","
                + "\"content\":null,\"status\":\"NONE\",\"favorite\":false,\"orderIndex\":0,"
                + "\"createdAt\":1,\"updatedAt\":2,\"futureField\":\"ignored\"}]}";
        BackupSnapshot back = ImportJsonReader.read(new StringReader(json));
        assertEquals(1, back.getNodes().size());
        assertEquals("Raft", back.getNodes().get(0).getTitle());
        assertNull(back.getNodes().get(0).getContent());
    }

    @Test
    public void missingSectionsBecomeEmptyLists() throws IOException {
        String json = "{\"format\":\"mocha.backup\",\"version\":1}";
        BackupSnapshot back = ImportJsonReader.read(new StringReader(json));
        assertTrue(back.getNodes().isEmpty());
        assertTrue(back.getResources().isEmpty());
    }

    @Test
    public void unknownEnumValuesFallBackInsteadOfCrashing() throws IOException {
        String json = "{\"format\":\"mocha.backup\",\"version\":1,"
                + "\"nodes\":[{\"id\":1,\"type\":\"POST\",\"title\":\"X\",\"status\":\"NAPPING\","
                + "\"favorite\":false,\"orderIndex\":0,\"createdAt\":0,\"updatedAt\":0}],"
                + "\"resources\":[{\"postId\":1,\"type\":\"HOLOGRAM\",\"title\":\"t\",\"url\":\"u\"}]}";
        BackupSnapshot back = ImportJsonReader.read(new StringReader(json));
        assertEquals(LearningStatus.NONE, back.getNodes().get(0).getStatus());
        assertEquals(ResourceType.OTHER, back.getResources().get(0).getType());
    }

    @Test
    public void rejectsANodeWithNoTitle() {
        String json = "{\"format\":\"mocha.backup\",\"version\":1,"
                + "\"nodes\":[{\"id\":1,\"type\":\"POST\",\"title\":\"  \"}]}";
        try {
            ImportJsonReader.read(new StringReader(json));
            fail("expected InvalidBackupException");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("no title"));
        }
    }

    @Test
    public void handlesLargeLibrariesWithoutTruncating() throws IOException {
        Node[] many = new Node[500];
        for (int i = 0; i < many.length; i++) {
            many[i] = new Node(
                    i + 1, null, NodeType.POST, "Post " + i, "body " + i,
                    LearningStatus.READING, false, i, 0L, 0L);
        }
        List<Node> nodes = Arrays.asList(many);
        BackupSnapshot back = roundTrip(new BackupSnapshot(
                nodes,
                Collections.<Link>emptyList(),
                Collections.<Tag>emptyList(),
                Collections.<PostTag>emptyList(),
                Collections.<DictionaryEntry>emptyList(),
                Collections.<ResourceItem>emptyList()));
        assertEquals(500, back.getNodes().size());
        assertEquals("Post 499", back.getNodes().get(499).getTitle());
    }
}
