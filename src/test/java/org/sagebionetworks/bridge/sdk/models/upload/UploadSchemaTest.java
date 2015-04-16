package org.sagebionetworks.bridge.sdk.models.upload;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.google.common.collect.ImmutableList;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.Test;

import org.sagebionetworks.bridge.sdk.Utilities;
import org.sagebionetworks.bridge.sdk.exceptions.BridgeSDKException;

@SuppressWarnings("unchecked")
public class UploadSchemaTest {
    @Test(expected = BridgeSDKException.class)
    public void nullFieldDefList() {
        new UploadSchema.Builder().withName("Test Schema").withSchemaId("test-schema").build();
    }

    @Test(expected = BridgeSDKException.class)
    public void emptyFieldDefList() {
        new UploadSchema.Builder().withFieldDefinitions(ImmutableList.<UploadFieldDefinition>of())
                .withName("Test Schema").withSchemaId("test-schema").build();
    }

    @Test(expected = BridgeSDKException.class)
    public void nullName() {
        new UploadSchema.Builder().withFieldDefinitions(mutableFieldDefList()).withSchemaId("test-schema").build();
    }

    @Test(expected = BridgeSDKException.class)
    public void emptyName() {
        new UploadSchema.Builder().withFieldDefinitions(mutableFieldDefList()).withName("").withSchemaId("test-schema")
                .build();
    }

    @Test(expected = BridgeSDKException.class)
    public void blankName() {
        new UploadSchema.Builder().withFieldDefinitions(mutableFieldDefList()).withName("   ")
                .withSchemaId("test-schema").build();
    }

    @Test(expected = BridgeSDKException.class)
    public void negativeRevision() {
        new UploadSchema.Builder().withFieldDefinitions(mutableFieldDefList()).withName("Test Schema").withRevision(-1)
                .withSchemaId("test-schema").build();
    }

    @Test(expected = BridgeSDKException.class)
    public void nullSchemaId() {
        new UploadSchema.Builder().withFieldDefinitions(mutableFieldDefList()).withName("Test Schema").build();
    }

    @Test(expected = BridgeSDKException.class)
    public void emptySchemaId() {
        new UploadSchema.Builder().withFieldDefinitions(mutableFieldDefList()).withName("Test Schema").withSchemaId("")
                .build();
    }

    @Test(expected = BridgeSDKException.class)
    public void blankSchemaId() {
        new UploadSchema.Builder().withFieldDefinitions(mutableFieldDefList()).withName("Test Schema")
                .withSchemaId("   ").build();
    }

    @Test
    public void happyCase() {
        List<UploadFieldDefinition> inputFieldDefList = mutableFieldDefList();
        UploadSchema testSchema = new UploadSchema.Builder().withFieldDefinitions(inputFieldDefList)
                .withName("Happy Case Schema").withSchemaId("happy-schema").build();
        assertEquals("Happy Case Schema", testSchema.getName());
        assertNull(testSchema.getRevision());
        assertEquals("happy-schema", testSchema.getSchemaId());

        // only check the field name, since everything else is tested by UploadFieldDefinitionTest
        List<UploadFieldDefinition> outputFieldDefList = testSchema.getFieldDefinitions();
        assertEquals(1, outputFieldDefList.size());
        assertEquals("test-field", outputFieldDefList.get(0).getName());

        // check that modifying the inputFieldDefList doesn't modify the outputFieldDefList
        inputFieldDefList.add(makeField("added-field", UploadFieldType.INT));
        assertEquals(1, outputFieldDefList.size());
    }

    @Test
    public void zeroRevision() {
        UploadSchema testSchema = new UploadSchema.Builder().withFieldDefinitions(mutableFieldDefList())
                .withName("Zero Revision Schema").withRevision(0).withSchemaId("zero-revision-schema").build();
        assertEquals("Zero Revision Schema", testSchema.getName());
        assertEquals(0, testSchema.getRevision().intValue());
        assertEquals("zero-revision-schema", testSchema.getSchemaId());

        // only check the field name, since everything else is tested by UploadFieldDefinitionTest
        List<UploadFieldDefinition> outputFieldDefList = testSchema.getFieldDefinitions();
        assertEquals(1, outputFieldDefList.size());
        assertEquals("test-field", outputFieldDefList.get(0).getName());
    }

    @Test
    public void positiveRevision() {
        UploadSchema testSchema = new UploadSchema.Builder().withFieldDefinitions(mutableFieldDefList())
                .withName("Positive Revision Schema").withRevision(1).withSchemaId("positive-revision-schema").build();
        assertEquals("Positive Revision Schema", testSchema.getName());
        assertEquals(1, testSchema.getRevision().intValue());
        assertEquals("positive-revision-schema", testSchema.getSchemaId());

        // only check the field name, since everything else is tested by UploadFieldDefinitionTest
        List<UploadFieldDefinition> outputFieldDefList = testSchema.getFieldDefinitions();
        assertEquals(1, outputFieldDefList.size());
        assertEquals("test-field", outputFieldDefList.get(0).getName());
    }

    @Test
    public void testSerialization() throws Exception {
        // start with JSON
        String jsonText = "{\n" +
                "   \"name\":\"Test Schema\",\n" +
                "   \"revision\":3,\n" +
                "   \"schemaId\":\"test-schema\",\n" +
                "   \"fieldDefinitions\":[\n" +
                "       {\n" +
                "           \"name\":\"foo\",\n" +
                "           \"type\":\"int\"\n" +
                "       }\n" +
                "   ]\n" +
                "}";

        // convert to POJO
        UploadSchema uploadSchema = Utilities.getMapper().readValue(jsonText, UploadSchema.class);
        assertEquals("Test Schema", uploadSchema.getName());
        assertEquals(3, uploadSchema.getRevision().intValue());
        assertEquals("test-schema", uploadSchema.getSchemaId());

        // only check the field name, since everything else is tested by UploadFieldDefinitionTest
        List<UploadFieldDefinition> fieldDefList = uploadSchema.getFieldDefinitions();
        assertEquals(1, fieldDefList.size());
        assertEquals("foo", fieldDefList.get(0).getName());

        // convert back to JSON
        String convertedJson = Utilities.getMapper().writeValueAsString(uploadSchema);

        // then convert to a map so we can validate the raw JSON
        Map<String, Object> jsonMap = Utilities.getMapper().readValue(convertedJson, Utilities.TYPE_REF_RAW_MAP);
        assertEquals(4, jsonMap.size());
        assertEquals("Test Schema", jsonMap.get("name"));
        assertEquals(3, jsonMap.get("revision"));
        assertEquals("test-schema", jsonMap.get("schemaId"));

        // only check the field name, since everything else is tested by UploadFieldDefinitionTest
        List<Map<String, Object>> fieldDefJsonList = (List<Map<String, Object>>) jsonMap.get("fieldDefinitions");
        assertEquals(1, fieldDefJsonList.size());
        assertEquals("foo", fieldDefJsonList.get(0).get("name"));
    }

    @Test
    public void equalsVerifier() {
        EqualsVerifier.forClass(UploadSchema.class).allFieldsShouldBeUsed().verify();
    }

    private static List<UploadFieldDefinition> mutableFieldDefList() {
        List<UploadFieldDefinition> fieldDefList = new ArrayList<>();
        fieldDefList.add(makeField("test-field", UploadFieldType.STRING));
        return fieldDefList;
    }

    private static UploadFieldDefinition makeField(String name, UploadFieldType type) {
        return new UploadFieldDefinition.Builder().withName(name).withType(type).build();
    }
}
