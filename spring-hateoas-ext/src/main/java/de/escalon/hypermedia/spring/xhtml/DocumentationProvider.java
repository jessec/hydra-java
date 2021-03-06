package de.escalon.hypermedia.spring.xhtml;

import de.escalon.hypermedia.AnnotatedParameter;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Provides documentation urls for the given elements.
 *
 * Created by Dietrich on 04.04.2015.
 */
public interface DocumentationProvider {

    /**
     * Gets documentationUrl for given parameter.
     * @param annotatedParameter to document
     * @param content current value
     * @return url or null
     */
    String getDocumentationUrl(AnnotatedParameter annotatedParameter, Object content);

    /**
     * Gets documentationUrl for given field.
     * @param field to document
     * @param content current value
     * @return url or null
     */
    String getDocumentationUrl(Field field, Object content);

    /**
     * Gets documentationUrl for given method.
     * @param method to document
     * @param content current value
     * @return url or null
     */
    String getDocumentationUrl(Method method, Object content);

    /**
     * Gets documentationUrl for given class.
     * @param clazz to document
     * @param content current value
     * @return url or null
     */
    String getDocumentationUrl(Class clazz, Object content);

    /**
     * Gets documentationUrl for given attribute name. Only makes sense if the attribute is in the current vocab.
     * @param name to document
     * @param content current value
     * @return url or null
     */
    String getDocumentationUrl(String name, Object content);
}
