/* Created by steinar on 08.01.12 at 21:46 */
package no.sr.ringo.persistence;

import com.google.inject.Inject;
import eu.peppol.identifier.ParticipantId;
import no.sr.ringo.ObjectMother;
import no.sr.ringo.account.RingoAccount;
import no.sr.ringo.cenbiimeta.ProfileId;
import no.sr.ringo.common.DatabaseHelper;
import no.sr.ringo.document.DocumentRepository;
import no.sr.ringo.document.FetchDocumentUseCase;
import no.sr.ringo.document.PeppolDocument;
import no.sr.ringo.guice.TestModuleFactory;
import no.sr.ringo.message.MessageNumber;
import no.sr.ringo.message.PeppolMessageNotFoundException;
import no.sr.ringo.message.TransferDirection;
import no.sr.ringo.peppol.*;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Guice;
import org.testng.annotations.Test;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

@Guice(moduleFactory = TestModuleFactory.class)
public class PeppolDocumentIntegrationTest {

    private final DatabaseHelper databaseHelper;
    private FetchDocumentUseCase fetchDocumentUseCase;
    private final DocumentRepository documentRepository;

    private RingoAccount account;
    private ParticipantId participantId;
    private List<MessageNumber> messagesToDelete;

    @Inject
    public PeppolDocumentIntegrationTest(FetchDocumentUseCase fetchDocumentUseCase, DocumentRepository documentRepository, DatabaseHelper databaseHelper) {
        this.fetchDocumentUseCase = fetchDocumentUseCase;
        this.documentRepository = documentRepository;
        this.databaseHelper = databaseHelper;
    }

    @BeforeMethod(groups = {"persistence"})
    public void setUp() throws SQLException {
        account = ObjectMother.getTestAccount();
        participantId = ObjectMother.getTestParticipantIdForSMPLookup();
        messagesToDelete = new ArrayList<MessageNumber>();
    }

    @AfterMethod(groups = {"persistence"})
    public void tearDown() throws SQLException {
        for (MessageNumber messageNumber : messagesToDelete) {
            databaseHelper.deleteMessage(messageNumber.toInt());
        }
    }

    @Test(groups = {"persistence"})
    public void testfindDocumentByMessageId() throws PeppolMessageNotFoundException {
        MessageNumber messageNumber = createMessageWithInvoiceDocument();
        final PeppolDocument xmlDoc = documentRepository.getPeppolDocument(account, messageNumber);
        assertNotNull(xmlDoc);
    }

    @Test(groups = {"persistence"}, expectedExceptions = PeppolMessageNotFoundException.class)
    public void testDoesNotFindDocumentByMessageId() throws Exception {
        //This should throw an exception as adam is not owner of message 1
        MessageNumber messageNumber = createMessageWithInvoiceDocument();
        documentRepository.getPeppolDocument(ObjectMother.getAdamsAccount(), messageNumber);
    }

    @Test(groups = {"persistence"})
    public void testStyleSheetAppliedForInvoice() throws PeppolMessageNotFoundException {

        //check for invoice
        MessageNumber messageNumber = createMessageWithInvoiceDocument();

        PeppolDocument xmlDoc = fetchDocumentUseCase.executeWithDecoration(account, messageNumber);

        assertEquals(xmlDoc.getXml(), "<?xml version=\"1.0\" encoding=\"UTF-8\" ?>\n" +
                "<?xml-stylesheet type=\"text/xsl\" href=\"/xslt/EHF-faktura_smaa.xslt\"?>\n" +
                "<Invoice>invoice</Invoice>");
        assertNotNull(xmlDoc);
    }

    @Test(groups = {"persistence"})
    public void testStyleSheetAppliedForCreditNote() throws PeppolMessageNotFoundException {

        //check for creditnote
        MessageNumber messageNumber = createMessageWithCreditNoteDocument();

        PeppolDocument xmlDoc = fetchDocumentUseCase.executeWithDecoration(account, messageNumber);
        assertEquals(xmlDoc.getXml(), "<?xml version=\"1.0\" encoding=\"UTF-8\" ?>\n" +
                "<?xml-stylesheet type=\"text/xsl\" href=\"/xslt/EHF-kreditnota_smaa.xslt\"?>\n" +
                "<CreditInvoice>invoice</CreditInvoice>");
        assertNotNull(xmlDoc);
    }

    private MessageNumber createMessageWithInvoiceDocument() {
        String invoiceXml = invoiceAsXml();
        PeppolDocumentTypeId invoiceDocumentType = new PeppolDocumentTypeId(
                RootNameSpace.INVOICE,
                LocalName.Invoice,
                CustomizationIdentifier.valueOf(TransactionIdentifier.Predefined.T010_INVOICE_V1 + ":#" + ProfileId.Predefined.PEPPOL_4A_INVOICE_ONLY + "#" + ProfileId.Predefined.EHF_INVOICE),
                "2.0");

        int message = databaseHelper.createMessage(invoiceDocumentType, invoiceXml, 1, TransferDirection.IN, participantId.stringValue(), participantId.stringValue(), UUID.randomUUID().toString(), null, null);
        return registerForDeletion(MessageNumber.create(message));
    }

    private MessageNumber createMessageWithCreditNoteDocument() {
        String creditXml = creditNoteAsXml();
        PeppolDocumentTypeId creditNoteDocumentType = new PeppolDocumentTypeId(
                RootNameSpace.CREDIT,
                LocalName.CreditNote,
                CustomizationIdentifier.valueOf(TransactionIdentifier.Predefined.T014_CREDIT_NOTE_V1 + ":#" + ProfileId.Predefined.PROPOSED_BII_XX + "#" + ProfileId.Predefined.EHF_CREDIT_NOTE),
                "2.0");

        int messageNumber = databaseHelper.createMessage(creditNoteDocumentType, creditXml, 1, TransferDirection.IN, participantId.stringValue(), participantId.stringValue(), UUID.randomUUID().toString(), null, null);
        return registerForDeletion(MessageNumber.create(messageNumber));
    }

    private String invoiceAsXml() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?><?xml-stylesheet type=\"text/xsl\" href=\"xxx.xslt\"?><Invoice>invoice</Invoice>";
    }

    private String creditNoteAsXml() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?><?xml-stylesheet type=\"text/xsl\" href=\"xxx.xslt\"?><CreditInvoice>invoice</CreditInvoice>";
    }

    private MessageNumber registerForDeletion(final MessageNumber messageNumber) {
        messagesToDelete.add(messageNumber);
        return messageNumber;
    }

}