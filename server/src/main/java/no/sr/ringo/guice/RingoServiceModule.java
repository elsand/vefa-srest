package no.sr.ringo.guice;

import com.google.inject.AbstractModule;
import com.google.inject.Singleton;
import com.google.inject.servlet.RequestScoped;
import no.sr.ringo.document.DocumentRepository;
import no.sr.ringo.document.DocumentRepositoryImpl;
import no.sr.ringo.document.PeppolDocumentFactory;
import no.sr.ringo.document.PeppolDocumentFactoryImpl;
import no.sr.ringo.email.EmailService;
import no.sr.ringo.email.NoEmailServiceImpl;
import no.sr.ringo.message.PeppolMessageRepository;
import no.sr.ringo.message.PeppolMessageRepositoryImpl;
import no.sr.ringo.report.ReportRepository;
import no.sr.ringo.report.ReportRepositoryImpl;

/**
 * Bindings for our service objects as used in RingoServer.
 *
 * @author andy
 * @author thore
 */
public class RingoServiceModule extends AbstractModule {

    /**
     * Configures a {@link com.google.inject.Binder} via the exposed methods.
     */
    @Override
    protected void configure() {
        bindRepositories();
        bindPeppolDocumentFactories();
        bindEmailService();
    }


    private void bindRepositories() {
        // The main workhorse
        bind(PeppolMessageRepository.class).to(PeppolMessageRepositoryImpl.class).in(Singleton.class);
        bind(ReportRepository.class).to(ReportRepositoryImpl.class).in(Singleton.class);
        bind(DocumentRepository.class).to(DocumentRepositoryImpl.class).in(RequestScoped.class);
    }

    private void bindPeppolDocumentFactories() {
        bind(PeppolDocumentFactory.class).to(PeppolDocumentFactoryImpl.class).in(Singleton.class);
    }

    private void bindEmailService() {
        bind(EmailService.class).to(NoEmailServiceImpl.class).in(Singleton.class);
    }

}