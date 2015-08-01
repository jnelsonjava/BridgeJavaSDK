package org.sagebionetworks.bridge.sdk.models.surveys;

import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;

import org.junit.Test;

public class SurveyResponseTest {
    @Test
    public void equalsContract() {
        EqualsVerifier.forClass(SurveyResponse.class).suppress(Warning.NONFINAL_FIELDS).allFieldsShouldBeUsed().verify();
    }

}
