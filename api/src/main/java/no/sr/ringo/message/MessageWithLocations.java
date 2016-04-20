package no.sr.ringo.message;

import java.net.URI;

/**
 * User: andy
 * Date: 1/20/12
 * Time: 3:48 PM
 */
public interface MessageWithLocations extends MessageMetaData {

    /**
     * Gets the URI corresponding to this resource
     * @return
     */
    URI getSelfURI();

    /**
     * Gets the URI corresponding to the PEPPOL XML Document
     * @return
     */
    URI getXmlDocumentURI();

    
}
