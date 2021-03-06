package de.escalon.hypermedia.spring.xhtml;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import de.escalon.hypermedia.DataType;
import de.escalon.hypermedia.PropertyUtils;
import de.escalon.hypermedia.action.ActionDescriptor;
import de.escalon.hypermedia.action.ActionInputParameter;
import de.escalon.hypermedia.action.Type;
import de.escalon.hypermedia.spring.Affordance;
import de.escalon.hypermedia.spring.PartialUriTemplate;
import org.apache.commons.lang3.StringUtils;
import org.springframework.core.MethodParameter;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.convert.Property;
import org.springframework.hateoas.Link;
import org.springframework.hateoas.TemplateVariable;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.RequestMethod;

import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.io.IOException;
import java.io.Writer;
import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;

import static de.escalon.hypermedia.spring.xhtml.XhtmlWriter.OptionalAttributes.attr;

/**
 * Created by Dietrich on 09.02.2015.
 */
public class XhtmlWriter extends Writer {
    private Writer writer;
    private List<String> stylesheets = Collections.emptyList();

    public static final String HTML_HEAD_START = "" + //
            //"<?xml version='1.0' encoding='UTF-8' ?>" + // formatter
            "<!DOCTYPE html>" + //
            "<html xmlns='http://www.w3.org/1999/xhtml'>" + //
            "  <head>" + //
            "    <title>%s</title>";


    public static final String HTML_STYLESHEET = "" + //
            "    <link rel=\"stylesheet\" href=\"%s\"  />";

    public static final String HTML_HEAD_END = "" + //
            "  </head>" + //
            "  <body>" + //
            "    <div class=\"container\">\n" + //
            "      <div class=\"row\">";

    public static final String HTML_END = "" + //
            "      </div>" +
            "    </div>" +
            "  </body>" + //
            "</html>";

    private String methodParam = "_method";
    private DocumentationProvider documentationProvider;


    public XhtmlWriter(Writer writer) {
        this.writer = writer;
    }

    public void setMethodParam(String methodParam) {
        this.methodParam = methodParam;
    }

    public void beginHtml(String title) throws IOException {
        write(String.format(HTML_HEAD_START, title));
        for (String stylesheet : stylesheets) {
            write(String.format(HTML_STYLESHEET, stylesheet));
        }
        write(String.format(HTML_HEAD_END, title));
    }


    public void endHtml() throws IOException {
        write(HTML_END);
    }

    public void beginDiv() throws IOException {
        writer.write("<div>");
    }

    public void endDiv() throws IOException {
        writer.write("</div>");
    }

    @Override
    public void write(char[] cbuf, int off, int len) throws IOException {
        writer.write(cbuf, off, len);
    }

    @Override
    public void flush() throws IOException {
        writer.flush();
    }

    @Override
    public void close() throws IOException {
        writer.close();
    }

    public void beginUnorderedList() throws IOException {
        writer.write("<ul class=\"list-group\">");
    }

    public void endUnorderedList() throws IOException {
        writer.write("</ul>");
    }

    public void beginListItem() throws IOException {
        writer.write("<li class=\"list-group-item\">");
    }

    public void endListItem() throws IOException {
        writer.write("</li>");
    }


    public void beginSpan() throws IOException {
        writer.write("<span>");
    }

    public void endSpan() throws IOException {
        writer.write("</span>");
    }

    public void beginDl() throws IOException {
        // TODO: make this configurable?
//        writer.write("<dl class=\"dl-horizontal\"  >");
        writer.write("<dl >");
    }

    public void endDl() throws IOException {
        writer.write("</dl>");
    }


    public void beginDt() throws IOException {
        writer.write("<dt>");
    }

    public void endDt() throws IOException {
        writer.write("</dt>");
    }

    public void beginDd() throws IOException {
        writer.write("<dd>");
    }

    public void endDd() throws IOException {
        writer.write("</dd>");
    }

    public void writeSpan(Object value) throws IOException {
        beginSpan();
        writer.write(value.toString());
        endSpan();
    }

    public void writeDefinitionTerm(Object value) throws IOException {
        beginDt();
        writer.write(value.toString());
        endDt();
    }

    public void setStylesheets(List<String> stylesheets) {
        Assert.notNull(stylesheets);
        this.stylesheets = stylesheets;
    }

    public void setDocumentationProvider(DocumentationProvider documentationProvider) {
        this.documentationProvider = documentationProvider;
    }

    public static class OptionalAttributes {

        private Map<String, String> attributes = new LinkedHashMap<String, String>();

        /**
         * Creates OptionalAttributes with one optional attribute having name if value is not null.
         *
         * @param name  of first attribute
         * @param value may be null
         * @return builder with one attribute, attr builder if value is null
         */
        public static OptionalAttributes attr(String name, String value) {
            Assert.isTrue(name != null && value != null || value == null);
            OptionalAttributes attributeBuilder = new OptionalAttributes();
            addAttributeIfValueNotNull(name, value, attributeBuilder);
            return attributeBuilder;
        }

        private static void addAttributeIfValueNotNull(String name, String value, OptionalAttributes attributeBuilder) {
            if (value != null) {
                attributeBuilder.attributes.put(name, value);
            }
        }


        public OptionalAttributes and(String name, String value) {
            addAttributeIfValueNotNull(name, value, this);
            return this;
        }

        public Map<String, String> build() {
            return attributes;
        }

        /**
         * Creates OptionalAttributes builder.
         *
         * @return builder
         */
        public static OptionalAttributes attr() {
            return attr(null, null);
        }
    }


    public void writeLinks(List<Link> links) throws IOException {
        for (Link link : links) {

            if (link instanceof Affordance) {
                Affordance affordance = (Affordance) link;
                List<ActionDescriptor> actionDescriptors = affordance.getActionDescriptors();
                if (actionDescriptors.isEmpty()) {
                    writeLinkWithoutActionDescriptor(affordance);
                } else {
                    if (affordance.isTemplated()) {
                        // TODO ensure that template expansion takes place for base uri
                        if (!(new PartialUriTemplate(affordance.getHref()).
                                expand(Collections.<String, Object>emptyMap())
                                .isBaseUriTemplated())) {

                            // TODO using appendForm here would require to calculate the baseUri in appendForm
                            // so that it is no longer templated
                            // (strip optional params and resolve plain base uri,
                            // partial uritemplate query must become hidden field
                            // can't render the form if base uri isn't plain)
                            // html does not allow templated action attr for POST or GET

                            Affordance expanded = affordance.expand();
                            // GET form for template
                            for (ActionDescriptor actionDescriptor : actionDescriptors) {
                                RequestMethod httpMethod = actionDescriptor.getHttpMethod();
                                if (RequestMethod.GET == httpMethod) {
                                    // TODO duplicate in writeLinkWithoutActionDescriptor
                                    String formName = actionDescriptor.getActionName();

                                    beginForm(OptionalAttributes.attr("action", expanded.getHref())
                                            .and("method", "GET")
                                            .and("name", formName));

                                    write("<h3>");
                                    String formH1 = "Form " + formName;
                                    write(formH1);
                                    write("</h3>");

                                    List<TemplateVariable> variables = link.getVariables();
                                    for (TemplateVariable variable : variables) {
                                        String variableName = variable.getName();
                                        String label = variable.hasDescription() ? variable.getDescription() :
                                                variableName;
                                        ActionInputParameter actionInputParameter =
                                                actionDescriptor.getActionInputParameter(variableName);
                                        String documentationUrl = documentationProvider.getDocumentationUrl(
                                                actionInputParameter, actionInputParameter.getCallValue());
                                        writeLabelWithDoc(variableName, variableName, documentationUrl);
                                        input(variableName, Type.TEXT);
                                    }
                                    inputButton(Type.SUBMIT, "Query");
                                    endForm();
                                }
                            }
                        }
                        // TODO write human-readable description of additional methods?
                    } else {

                        for (ActionDescriptor actionDescriptor : actionDescriptors) {
                            // TODO write documentation about the supported action and maybe fields?
                            // especially to make PUT and DELETE obvious
                            appendForm(affordance, actionDescriptor);
                        }
                    }
                }
            } else { // simple link, may be templated
                writeLinkWithoutActionDescriptor(link);
            }
        }
    }

    private void writeLinkWithoutActionDescriptor(Link link) throws IOException {
        if (link.isTemplated()) {
            // TODO ensure that template expansion takes place for base uri
            Link expanded = link.expand();
            beginForm(OptionalAttributes.attr("action", expanded.getHref())
                    .and("method", "GET"));
            List<TemplateVariable> variables = link.getVariables();
            for (TemplateVariable variable : variables) {
                String variableName = variable.getName();
                String label = variable.hasDescription() ? variable.getDescription() : variableName;
                writeLabelWithDoc(label, variableName, null); // no documentation url
                input(variableName, Type.TEXT);
            }
        } else {
            String rel = link.getRel();
            String title = (rel != null ? rel : link.getHref());
            // TODO: write link instead of anchor here?
            writeAnchor(OptionalAttributes.attr("href", link.getHref()), title);
        }
    }

    /**
     * Classic submit or reset button.
     *
     * @param type  submit or reset
     * @param value caption on the button
     * @throws IOException
     */
    private void inputButton(Type type, String value) throws IOException {
        write("<input type=\"");
        write(type.toString());
        write("\" ");
        write("value");
        write("=");
        quote();
        write(value);
        quote();
        write("/>");
    }

    private void input(String fieldName, Type type, OptionalAttributes attributes) throws IOException {
        write("<input name=\"");
        write(fieldName);
        write("\" type=\"");
        write(type.toString());
        write("\" ");
        writeAttributes(attributes);
        write("/>");
    }

    private void input(String fieldName, Type type) throws IOException {
        input(fieldName, type, OptionalAttributes.attr());
    }

//    private void beginLabel(String label) throws IOException {
//        beginLabel(label, attr());
//    }

//    private void beginLabel(String label, OptionalAttributes attributes) throws IOException {
//        beginLabel(attributes);
//        write(label);
//    }

    private void beginLabel(OptionalAttributes attributes) throws IOException {
        write("<label");
        writeAttributes(attributes);
        endTag();
    }

    private void endLabel() throws IOException {
        write("</label>");
    }


    private void beginForm(OptionalAttributes attrs) throws IOException {
        write("<form class=\"well\" ");
        writeAttributes(attrs);
        write(">");
    }

    private void writeAttributes(OptionalAttributes attrs) throws IOException {
        Map<String, String> attributes = attrs.build();
        for (Map.Entry<String, String> entry : attributes.entrySet()) {
            write(" ");
            write(entry.getKey());
            write("=");
            quote();
            write(entry.getValue());
            quote();
        }
    }

    private void quote() throws IOException {
        write("\"");
    }

    private void endForm() throws IOException {
        write("</form>");
    }

    public void beginAnchor(OptionalAttributes attrs) throws IOException {
        write("<a ");
        writeAttributes(attrs);
        endTag();
    }

    public void endAnchor() throws IOException {
        write("</a>");
    }

    private void writeAnchor(OptionalAttributes attrs, String value) throws IOException {
        beginAnchor(attrs);
        write(value);
        endAnchor();
    }

    /**
     * Appends form and squashes non-GET or POST to POST. Always adds _method field for handling by an appropriate
     * filter such as Spring's HiddenHttpMethodFilter.
     *
     * @param affordance       to make into a form
     * @param actionDescriptor describing the form action
     * @throws IOException
     * @see <a href="http://docs.spring.io/spring/docs/3.0.x/javadoc-api/org/springframework/web/filter/HiddenHttpMethodFilter.html">Spring MVC HiddenHttpMethodFilter</a>
     */
    private void appendForm(Affordance affordance, ActionDescriptor actionDescriptor) throws IOException {
        String formName = actionDescriptor.getActionName();
        RequestMethod httpMethod = actionDescriptor.getHttpMethod();

        beginForm(OptionalAttributes.attr("action", affordance.getHref())
                .and("method", getHtmlConformingHttpMethod(httpMethod))
                .and("name", formName));
        write("<h4>");
        String formH1 = "Form " + formName;
        write(formH1);
        write("</h4>");

        writeHiddenHttpMethodField(actionDescriptor.getHttpMethod());
        // build the form
        if (actionDescriptor.hasRequestBody()) {
            ActionInputParameter requestBody = actionDescriptor.getRequestBody();
            Class<?> nestedParameterType = requestBody.getNestedParameterType();
            recurseBeanProperties(nestedParameterType, actionDescriptor, requestBody, requestBody.getCallValue());
        } else {
            Collection<String> requestParams = actionDescriptor.getRequestParamNames();
            for (String requestParamName : requestParams) {
                ActionInputParameter actionInputParameter = actionDescriptor.getActionInputParameter(requestParamName);

                // TODO support list and matrix parameters?
                Object[] possibleValues = actionInputParameter.getPossibleValues(actionDescriptor);
                if (possibleValues.length > 0) {
                    if (actionInputParameter.isArrayOrCollection()) {
                        appendSelectMulti(requestParamName, possibleValues, actionInputParameter);
                    } else {
                        appendSelectOne(requestParamName, possibleValues, actionInputParameter);
                    }
                } else {
                    if (actionInputParameter.isArrayOrCollection()) {
                        // TODO support for free list input?
                        Object[] callValues = actionInputParameter.getCallValues();
                        int items = callValues.length;
                        for (int i = 0; i < items; i++) {
                            Object value;
                            if (i < callValues.length) {
                                value = callValues[i];
                            } else {
                                value = null;
                            }
                            appendInput(requestParamName, actionInputParameter, value);
                        }
                    } else {
                        String callValueFormatted = actionInputParameter.getCallValueFormatted();
                        appendInput(requestParamName, actionInputParameter, callValueFormatted);
                    }
                }
            }
        }

        inputButton(Type.SUBMIT, StringUtils.capitalize(httpMethod.name()
                .toLowerCase()));
        endForm();
    }

    private void writeHiddenHttpMethodField(RequestMethod httpMethod) throws IOException {
        switch (httpMethod) {
            case GET:
            case POST:
                break;
            default:
                input(methodParam, Type.HIDDEN, OptionalAttributes.attr("value", httpMethod.name()));
        }
    }

    private String getHtmlConformingHttpMethod(RequestMethod requestMethod) {
        String ret;
        switch (requestMethod) {
            case GET:
            case POST:
                ret = requestMethod.name();
                break;
            default:
                ret = RequestMethod.POST.name();
        }
        return ret;
    }

    private Constructor findDefaultCtor(Constructor[] constructors) {
        // TODO duplicate on HtmlResourceMessageConverter
        Constructor constructor = null;
        for (Constructor ctor : constructors) {
            if (ctor.getParameterTypes().length == 0) {
                constructor = ctor;
            }
        }
        return constructor;
    }

    private Constructor findJsonCreator(Constructor[] constructors) {
        // TODO duplicate on HtmlResourceMessageConverter
        Constructor constructor = null;
        for (Constructor ctor : constructors) {
            if (AnnotationUtils.getAnnotation(ctor, JsonCreator.class) != null) {
                constructor = ctor;
                break;
            }
        }
        return constructor;
    }

    /**
     * Renders input fields for bean properties of bean to add or update or patch.
     *
     * @param beanType             to render
     * @param actionDescriptor     which describes the method
     * @param actionInputParameter which requires the bean
     * @param currentCallValue     sample call value
     * @throws IOException
     */
    private void recurseBeanProperties(Class<?> beanType, ActionDescriptor actionDescriptor, ActionInputParameter
            actionInputParameter, Object currentCallValue) throws IOException {
        // TODO support Option provider by other method args?
        final BeanInfo beanInfo = getBeanInfo(beanType);
        final PropertyDescriptor[] propertyDescriptors = beanInfo.getPropertyDescriptors();
        // TODO collection and map

        // TODO: do not add two inputs for setter and ctor

        // TODO almost duplicate of HtmlResourceMessageConverter.recursivelyCreateObject
        if (RequestMethod.POST == actionDescriptor.getHttpMethod()) {
            try {
                Constructor[] constructors = beanType.getConstructors();
                // find default ctor
                Constructor constructor = findDefaultCtor(constructors);
                // find ctor with JsonCreator ann
                if (constructor == null) {
                    constructor = findJsonCreator(constructors);
                }
                Assert.notNull(constructor, "no default constructor or JsonCreator found for type " + beanType
                        .getName());
                int parameterCount = constructor.getParameterTypes().length;
                if (parameterCount > 0) {
                    Annotation[][] annotationsOnParameters = constructor.getParameterAnnotations();

                    Class[] parameters = constructor.getParameterTypes();
                    int paramIndex = 0;
                    for (Annotation[] annotationsOnParameter : annotationsOnParameters) {
                        for (Annotation annotation : annotationsOnParameter) {
                            if (JsonProperty.class == annotation.annotationType()) {
                                JsonProperty jsonProperty = (JsonProperty) annotation;
                                // TODO use required attribute of JsonProperty
                                String paramName = jsonProperty.value();
                                Class parameterType = parameters[paramIndex];

                                // TODO duplicate below for PropertyDescriptors
                                if (DataType.isSingleValueType(parameterType)) {

                                    Object propertyValue = getPropertyOrFieldValue(currentCallValue, paramName);

                                    ActionInputParameter constructorParamInputParameter = new ActionInputParameter
                                            (new MethodParameter(constructor, paramIndex), propertyValue);

                                    final Object[] possibleValues = actionInputParameter.getPossibleValues(new
                                            MethodParameter(constructor, paramIndex), actionDescriptor);
                                    if (possibleValues.length > 0) {
                                        if (actionInputParameter.isArrayOrCollection()) {
                                            // TODO multiple formatted callvalues
                                            appendSelectMulti(paramName, possibleValues,
                                                    constructorParamInputParameter);
                                        } else {
                                            appendSelectOne(paramName, possibleValues, constructorParamInputParameter);
                                        }
                                    } else {
                                        appendInput(paramName, constructorParamInputParameter,
                                                constructorParamInputParameter.getCallValue());
                                    }
                                } else if (DataType.isArrayOrCollection(parameterType)) {
                                    Object[] callValues = actionInputParameter.getCallValues();
                                    int items = callValues.length;
                                    for (int i = 0; i < items; i++) {
                                        Object value;
                                        if (i < callValues.length) {
                                            value = callValues[i];
                                        } else {
                                            value = null;
                                        }
                                        recurseBeanProperties(actionInputParameter.getNestedParameterType(),
                                                actionDescriptor, actionInputParameter, value);
                                    }
                                } else {
                                    beginDiv();
                                    write(paramName + ":");
                                    Object propertyValue = getPropertyOrFieldValue(currentCallValue, paramName);
                                    recurseBeanProperties(parameterType, actionDescriptor, actionInputParameter,
                                            propertyValue);
                                    endDiv();
                                }
                                paramIndex++; // increase for each @JsonProperty
                            }
                        }
                    }
                    Assert.isTrue(parameters.length == paramIndex,
                            "not all constructor arguments of @JsonCreator " + constructor.getName() +
                                    " are annotated with @JsonProperty");
                }
            } catch (Exception e) {
                throw new RuntimeException("Failed to write input fields for constructor", e);
            }
        } else { // non-POST

            // TODO non-writable properties and public fields; make sure the inputs are part of a form
            // write input field for every setter
            for (PropertyDescriptor propertyDescriptor : propertyDescriptors) {
                final Method writeMethod = propertyDescriptor.getWriteMethod();
                if (writeMethod == null) {
                    continue;
                }
                final Class<?> propertyType = propertyDescriptor.getPropertyType();

                String propertyName = propertyDescriptor.getName();

                if (DataType.isSingleValueType(propertyType)) {

                    final Property property = new Property(beanType, propertyDescriptor.getReadMethod(),
                            propertyDescriptor.getWriteMethod(), propertyDescriptor.getName());

                    Object propertyValue = getPropertyOrFieldValue(currentCallValue, propertyName);
                    MethodParameter methodParameter = new MethodParameter(propertyDescriptor.getWriteMethod(), 0);
                    ActionInputParameter propertySetterInputParameter = new ActionInputParameter(methodParameter,
                            propertyValue);
                    final Object[] possibleValues = actionInputParameter.getPossibleValues(methodParameter,
                            actionDescriptor);
                    if (possibleValues.length > 0) {
                        if (actionInputParameter.isArrayOrCollection()) {
                            // TODO multiple formatted callvalues
                            appendSelectMulti(propertyName, possibleValues, propertySetterInputParameter);
                        } else {
                            appendSelectOne(propertyName, possibleValues, propertySetterInputParameter);
                        }
                    } else {
                        //String callValueFormatted = actionInputParameter.getCallValueFormatted();
                        appendInput(propertyName, propertySetterInputParameter, propertySetterInputParameter
                                .getCallValue());
                    }
                } else if (actionInputParameter.isArrayOrCollection()) {
                    Object[] callValues = actionInputParameter.getCallValues();
                    int items = callValues.length;
                    for (int i = 0; i < items; i++) {
                        Object value;
                        if (i < callValues.length) {
                            value = callValues[i];
                        } else {
                            value = null;
                        }
                        recurseBeanProperties(actionInputParameter.getNestedParameterType(), actionDescriptor,
                                actionInputParameter, value);
                    }
                } else {
                    beginDiv();
                    write(propertyName + ":");
                    Object propertyValue = PropertyUtils.getPropertyValue(currentCallValue, propertyDescriptor);


                    recurseBeanProperties(propertyType, actionDescriptor, actionInputParameter, propertyValue);
                    endDiv();
                }
            }
        }
    }

    // TODO move to PropertyUtil and remove current method for propertyDescriptors, cache search results
    private Object getPropertyOrFieldValue(Object currentCallValue, String propertyOrFieldName) {
        if (currentCallValue == null) {
            return null;
        }
        Object propertyValue = getBeanPropertyValue(currentCallValue, propertyOrFieldName);
        if (propertyValue == null) {
            propertyValue = getFieldValue(currentCallValue, propertyOrFieldName);
        }
        return propertyValue;
    }

    private Object getFieldValue(Object currentCallValue, String fieldName) {
        try {
            Class<?> beanType = currentCallValue.getClass();
            Object propertyValue = null;
            Field[] fields = beanType.getFields();
            for (Field field : fields) {
                if (fieldName.equals(field.getName())) {
                    propertyValue = field.get(currentCallValue);
                    break;
                }
            }
            return propertyValue;
        } catch (Exception e) {
            throw new RuntimeException("Failed to read field " + fieldName + " from " + currentCallValue.toString(), e);
        }
    }


    // TODO move to PropertyUtil and remove current method for propertyDescriptors
    private Object getBeanPropertyValue(Object currentCallValue, String paramName) {
        if (currentCallValue == null) {
            return null;
        }
        try {
            Object propertyValue = null;
            BeanInfo info = Introspector.getBeanInfo(currentCallValue.getClass());
            PropertyDescriptor[] pds = info.getPropertyDescriptors();
            for (PropertyDescriptor pd : pds) {
                if (paramName.equals(pd.getName())) {
                    Method readMethod = pd.getReadMethod();
                    if (readMethod != null) {
                        propertyValue = readMethod.invoke(currentCallValue);
                    }
                    break;
                }
            }
            return propertyValue;
        } catch (Exception e) {
            throw new RuntimeException("Failed to read property " + paramName + " from " + currentCallValue.toString
                    (), e);
        }
    }


    private BeanInfo getBeanInfo(Class<?> beanType) {
        try {
            return Introspector.getBeanInfo(beanType);
        } catch (IntrospectionException e) {
            throw new RuntimeException(e);
        }
    }

    private void appendInput(String requestParamName, ActionInputParameter actionInputParameter, Object value) throws
            IOException {
        if (actionInputParameter.isRequestBody()) { // recurseBeanProperties does that
            throw new IllegalArgumentException("cannot append input field for requestBody");
        }
        String fieldLabel = requestParamName;


        Type htmlInputFieldType = actionInputParameter.getHtmlInputFieldType();
        String val = value == null ? "" : value.toString();
        beginDiv();
        if (Type.HIDDEN == htmlInputFieldType) {
            input(requestParamName, htmlInputFieldType, OptionalAttributes.attr("value", val));
        } else {
            String documentationUrl = documentationProvider.getDocumentationUrl(actionInputParameter, value);
            if (actionInputParameter.hasInputConstraints()) {
                writeLabelWithDoc(fieldLabel, requestParamName, documentationUrl);
                OptionalAttributes attrs = OptionalAttributes.attr();
                for (Map.Entry<String, Object> entry : actionInputParameter.getInputConstraints()
                        .entrySet()) {
                    attrs.and(entry.getKey(), entry.getValue()
                            .toString());
                }
                attrs.and("value", val);
                input(requestParamName, htmlInputFieldType, attrs);
            } else {
                writeLabelWithDoc(fieldLabel, requestParamName, documentationUrl);
                input(requestParamName, htmlInputFieldType, attr("value", val));
            }
        }
        endDiv();

    }

    private void writeLabelWithDoc(String label, String fieldName, String documentationUrl) throws IOException {
        beginLabel(OptionalAttributes.attr("for", fieldName));
        if (documentationUrl == null) {
            write(label);
        } else {
            beginAnchor(XhtmlWriter.OptionalAttributes.attr("href", documentationUrl)
                    .and("title", documentationUrl));
            write(label);
            endAnchor();
        }
        endLabel();
    }


    private void appendSelectOne(String requestParamName, Object[] possibleValues, ActionInputParameter actionInputParameter)
            throws IOException {
        beginDiv();
        Object callValue = actionInputParameter.getCallValue();
        String documentationUrl = documentationProvider.getDocumentationUrl(actionInputParameter, callValue);
        writeLabelWithDoc(requestParamName, requestParamName, documentationUrl);
        beginSelect(requestParamName, requestParamName, possibleValues.length);
        for (Object possibleValue : possibleValues) {
            if (possibleValue.equals(callValue)) {
                option(possibleValue.toString(), attr("selected", "selected"));
            } else {
                option(possibleValue.toString());
            }
        }
        endSelect();

        endDiv();
    }


    private void appendSelectMulti(String requestParamName, Object[] possibleValues, ActionInputParameter actionInputParameter) throws IOException {
        beginDiv();
        Object[] actualValues = actionInputParameter.getCallValues();
        final Object aCallValue;
        if (actualValues.length > 0) {
            aCallValue = actualValues[0];
        } else {
            aCallValue = null;
        }
        String documentationUrl = documentationProvider.getDocumentationUrl(actionInputParameter, aCallValue);
        writeLabelWithDoc(requestParamName, requestParamName, documentationUrl);
        beginSelect(requestParamName, requestParamName, possibleValues.length, attr("multiple", "multiple"));
        for (Object possibleValue : possibleValues) {
            if (arrayContains(actualValues, possibleValue)) {
                option(possibleValue.toString(), attr("selected", "selected"));
            } else {
                option(possibleValue.toString());
            }
        }
        endForm();
        endDiv();
    }

    private void option(String option) throws IOException {
        option(option, attr());
    }

    private void option(String option, OptionalAttributes attr) throws IOException {
        // <option selected='selected'>%s</option>
        beginTag("option");
        writeAttributes(attr);
        endTag();
        write(option);
        write("</option>");
    }

    private void beginTag(String tag) throws IOException {
        write("<");
        write(tag);

    }

    private void endTag() throws IOException {
        write(">");
    }

    private void beginSelect(String name, String id, int size) throws IOException {
        beginSelect(name, id, size, attr());
    }

    private void beginSelect(String name, String id, int size, OptionalAttributes attrs) throws IOException {
        beginTag("select");
        write(" name=");
        quote(name);
        write(" id=");
        quote(id);
        //write(" size=");
        //quote(Integer.toString(size));
        writeAttributes(attrs);
        endTag();
    }

    private void endSelect() throws IOException {
        write("</select>");
    }

    private void quote(String s) throws IOException {
        quote();
        write(s);
        quote();
    }


    private boolean arrayContains(Object[] values, Object value) {
        for (int i = 0; i < values.length; i++) {
            Object item = values[i];
            if (item.equals(value)) {
                return true;
            }
        }
        return false;
    }
}
