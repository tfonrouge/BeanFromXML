import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.Attribute;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

@SuppressWarnings("unused")
public class BeanFromXML {

    private static void getAttributes(Object object, XMLEvent event) {

        if (event.isStartElement()) {

            Iterator iterator = event.asStartElement().getAttributes();
            HashMap<String, String> mapValues = new HashMap<>();
            while (iterator.hasNext()) {
                Attribute attribute = (Attribute) iterator.next();
                mapValues.put(attribute.getName().getLocalPart().toUpperCase(), attribute.getValue());
            }

            Field[] fields = object.getClass().getDeclaredFields();

            for (Field field : fields) {
                String value = mapValues.get(field.getName().toUpperCase());
                if (value != null) {
                    boolean isAccesible = field.isAccessible();
                    try {
                        if (!isAccesible) {
                            field.setAccessible(true);
                        }
                        if (field.getType().equals(String.class)) {
                            field.set(object, value);
                        } else {
                            switch (field.getType().getSimpleName()) {
                                case "double":
                                case "Double":
                                    field.set(object, Double.valueOf(value));
                                    break;
                                case "integer":
                                case "Integer":
                                    field.set(object, Integer.valueOf(value));
                                    break;
                            }
                        }
                    } catch (IllegalAccessException e) {
                        e.printStackTrace();
                    } finally {
                        field.setAccessible(isAccesible);
                    }
                }
            }
        }
    }

    @SuppressWarnings("unused,WeakerAccess")
    public static void getBean(Object object, XMLEventReader eventReader, XMLEvent event) throws XMLStreamException {
        if (event.isStartElement()) {

            getAttributes(object, event);

            String endElementName = event.asStartElement().getName().getLocalPart();
            Field[] fields = object.getClass().getDeclaredFields();

            List<BXMLBaseField> bxmlBaseFields = new ArrayList<>();

            for (Field field : fields) {
                BXMLElementName annotation = field.getAnnotation(BXMLElementName.class);
                if (annotation != null) {
                    if (field.getType().isAssignableFrom(List.class)) {
                        ParameterizedType parameterizedType = (ParameterizedType) field.getGenericType();
                        Type[] types = parameterizedType.getActualTypeArguments();
                        if (types != null && types.length == 1) {
                            Class<?> classItem = (Class<?>) parameterizedType.getActualTypeArguments()[0];
                            BXMLElementName clsAnnotation = (classItem.getAnnotation(BXMLElementName.class));
                            if (clsAnnotation == null) {
                                bxmlBaseFields.add(new BXMLListField(field, classItem.getSimpleName(), classItem));
                            } else {
                                bxmlBaseFields.add(new BXMLListField(field, clsAnnotation.value(), classItem));
                            }
                        }
                    } else {
                        bxmlBaseFields.add(new BXMLItemField(field, annotation.value()));
                    }
                }
            }

            if (bxmlBaseFields.size() > 0) {
                bxmlBaseFields.forEach(bxmlBaseField -> {
                    if (bxmlBaseField instanceof BXMLListField) {
                        ((BXMLListField) bxmlBaseField).objects = new ArrayList<>();
                    }
                });

                /*
                Loops until reach end of node
                 */
                while (eventReader.hasNext() && (!event.isEndElement() || !event.asEndElement().getName().getLocalPart().equalsIgnoreCase(endElementName))) {
                    if (event.isStartElement()) {
                        StartElement startElement = event.asStartElement();
                        String elementName = startElement.getName().getLocalPart();
                        for (BXMLBaseField bxmlBaseField : bxmlBaseFields) {
                            if (bxmlBaseField.elementName.equalsIgnoreCase(elementName)) {
                                try {
                                    Object item = bxmlBaseField.getNewInstance();
                                    getBean(item, eventReader, event);
                                    if (bxmlBaseField instanceof BXMLListField) {
                                        ((BXMLListField) bxmlBaseField).objects.add(item);
                                    } else {
                                        bxmlBaseField.field.set(object, item);
                                    }
                                } catch (IllegalAccessException | InstantiationException e) {
                                    e.printStackTrace();
                                }
                            }
                        }
                    }
                    event = eventReader.nextEvent();
                }
                bxmlBaseFields.forEach(bxmlBaseField -> {
                    try {
                        if (bxmlBaseField instanceof BXMLListField) {
                            bxmlBaseField.field.set(object, ((BXMLListField) bxmlBaseField).objects);
                        }
                    } catch (IllegalAccessException e) {
                        e.printStackTrace();
                    } finally {
                        bxmlBaseField.field.setAccessible(bxmlBaseField.isAccessible);
                    }
                });
            }
        }
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.FIELD, ElementType.TYPE})
    public @interface BXMLElementName {
        String value();
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.FIELD})
    @SuppressWarnings("unused")
    /* TODO: implement */
    public @interface BXMLIgnore {

    }

    static abstract class BXMLBaseField {
        final Field field;
        final boolean isAccessible;
        final String elementName;

        BXMLBaseField(Field field, String elementName) {
            this.field = field;
            this.elementName = elementName;
            this.isAccessible = field.isAccessible();
            field.setAccessible(true);
        }

        abstract Object getNewInstance() throws IllegalAccessException, InstantiationException;
    }

    static class BXMLItemField extends BXMLBaseField {
        BXMLItemField(Field field, String elementName) {
            super(field, elementName);
        }

        @Override
        Object getNewInstance() throws IllegalAccessException, InstantiationException {
            return field.getType().newInstance();
        }
    }

    static class BXMLListField extends BXMLBaseField {
        final Class<?> clazz;
        List<Object> objects;

        BXMLListField(Field field, String elementName, Class<?> clazz) {
            super(field, elementName);
            this.clazz = clazz;
        }

        @Override
        Object getNewInstance() throws IllegalAccessException, InstantiationException {
            return clazz.newInstance();
        }
    }

}
