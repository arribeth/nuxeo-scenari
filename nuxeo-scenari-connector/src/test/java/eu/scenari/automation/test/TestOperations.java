package eu.scenari.automation.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.common.utils.FileUtils;
import org.nuxeo.ecm.automation.AutomationService;
import org.nuxeo.ecm.automation.OperationChain;
import org.nuxeo.ecm.automation.OperationContext;
import org.nuxeo.ecm.automation.OperationParameters;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentModelList;
import org.nuxeo.ecm.core.api.impl.blob.FileBlob;
import org.nuxeo.ecm.core.test.CoreFeature;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;

import com.google.inject.Inject;

import eu.scenari.automation.PublishFolder;

@RunWith(FeaturesRunner.class)
@Features(CoreFeature.class)
@Deploy({ "org.nuxeo.ecm.platform.content.template",
        "org.nuxeo.ecm.automation.core", "org.scenari.connector" })
public class TestOperations {

    @Inject
    AutomationService service;

    @Inject
    CoreSession session;

    @Test
    public void shouldADocument() throws Exception {

        OperationContext ctx = new OperationContext(session);

        OperationChain chain = new OperationChain("fakeChain");

        Map<String, Object> params = new HashMap<String, Object>();
        OperationParameters oparams = new OperationParameters(PublishFolder.ID,
                params);
        chain.add(oparams);

        File file = File.createTempFile("nx-test-blob-", ".tmp");
        FileUtils.writeFile(file, "blob content");

        Blob blob = new FileBlob(file);

        oparams.set("file", blob);

        DocumentModel doc = (DocumentModel) service.run(ctx, chain);

        assertNotNull(doc);

        // fush
        session.save();
        DocumentModelList docs = session.query("select * from File where dc:title='Scenari File'");
        assertNotNull(docs);
        assertEquals(1, docs.size());
    }
}
