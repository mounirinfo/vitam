package fr.gouv.vitam.common;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import fr.gouv.vitam.common.SedaVersion;

public class SedaVersionTest {
    
    @Test
    public void testSedaVersion() {
        final SedaVersion sedaVersion = new SedaVersion();
        sedaVersion.setBinaryDataObjectVersions(new String[] {"TEST1","TEST2"});
        sedaVersion.setPhysicalDataObjectVersions(new String[] {"TEST3","TEST4"});
        assertNotNull(sedaVersion.getVersionForType("BinaryDataObject"));
        assertNotNull(sedaVersion.getVersionForType("PhysicalDataObject"));
        assertEquals(0, sedaVersion.getVersionForType("Bleah").size());
        assertEquals("TEST1", sedaVersion.getVersionForType("BinaryDataObject").get(0));
        assertEquals("TEST4", sedaVersion.getVersionForType("PhysicalDataObject").get(1));
    }


}
