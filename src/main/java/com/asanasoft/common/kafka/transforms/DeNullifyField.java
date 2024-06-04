package com.asanasoft.common.kafka.transforms;

import org.apache.kafka.common.config.ConfigDef;
import org.apache.kafka.connect.connector.ConnectRecord;
import org.apache.kafka.connect.data.Field;
import org.apache.kafka.connect.data.Schema;
import org.apache.kafka.connect.data.Struct;
import org.apache.kafka.connect.errors.DataException;
import org.apache.kafka.connect.transforms.MaskField;
import org.apache.kafka.connect.transforms.Transformation;
import org.apache.kafka.connect.transforms.util.NonEmptyListValidator;
import org.apache.kafka.connect.transforms.util.SimpleConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.*;

import static org.apache.kafka.connect.transforms.util.Requirements.requireMap;
import static org.apache.kafka.connect.transforms.util.Requirements.requireStruct;

/**
 * Copied from org.apache.kafka.connect.transforms.MaskField
 * The difference is that this class only works with field values, and only
 * if the value is null. It replaces it with a "default null value" in accordance
 * to the field's schema.
 *
 * @param <R>
 */
public class DeNullifyField<R extends ConnectRecord<R>> implements Transformation<R> {
    private Logger logger = LoggerFactory.getLogger(DeNullifyField.class);

    public static final String OVERVIEW_DOC =
            "DeNullifies specified fields with a valid null value for the field type (i.e. 0, false, empty string, and so on)."
                    + "<p/>Use the concrete transformation type designed for the record key (<code>" + MaskField.Key.class.getName() + "</code>) "
                    + "or value (<code>" + MaskField.Value.class.getName() + "</code>).";

    private static final String FIELDS_CONFIG = "fields";

    public static final ConfigDef CONFIG_DEF = new ConfigDef()
            .define(FIELDS_CONFIG, ConfigDef.Type.LIST, ConfigDef.NO_DEFAULT_VALUE, new NonEmptyListValidator(), ConfigDef.Importance.HIGH, "Names of fields to denullify.");

    private static final String PURPOSE = "denullify fields";

    private static final Map<Class<?>, Object> PRIMITIVE_VALUE_MAPPING = new HashMap<>();

    static {
        PRIMITIVE_VALUE_MAPPING.put(Boolean.class, Boolean.FALSE);
        PRIMITIVE_VALUE_MAPPING.put(Byte.class, (byte) 0);
        PRIMITIVE_VALUE_MAPPING.put(Short.class, (short) 0);
        PRIMITIVE_VALUE_MAPPING.put(Integer.class, 0);
        PRIMITIVE_VALUE_MAPPING.put(Long.class, 0L);
        PRIMITIVE_VALUE_MAPPING.put(Float.class, 0f);
        PRIMITIVE_VALUE_MAPPING.put(Double.class, 0d);
        PRIMITIVE_VALUE_MAPPING.put(BigInteger.class, BigInteger.ZERO);
        PRIMITIVE_VALUE_MAPPING.put(BigDecimal.class, BigDecimal.ZERO);
        PRIMITIVE_VALUE_MAPPING.put(Date.class, new Date(0));
        PRIMITIVE_VALUE_MAPPING.put(String.class, "");
    }

    private Set<String> maskedFields;

    @Override
    public void configure(Map<String, ?> props) {
        final SimpleConfig config = new SimpleConfig(CONFIG_DEF, props);
        maskedFields = new HashSet<>(config.getList(FIELDS_CONFIG));
    }

    @Override
    public R apply(R record) {
        if (operatingSchema(record) == null) {
            return applySchemaless(record);
        } else {
            return applyWithSchema(record);
        }
    }

    protected R applySchemaless(R record) {
        final Map<String, Object> value = requireMap(operatingValue(record), PURPOSE);
        final HashMap<String, Object> updatedValue = new HashMap<>(value);
        for (String field : maskedFields) {
            logger.trace("Denullifying " + field);
            updatedValue.put(field, masked(value.get(field)));
        }
        return newRecord(record, updatedValue);
    }

    protected R applyWithSchema(R record) {
        final Struct value = requireStruct(operatingValue(record), PURPOSE);
        final Struct updatedValue = new Struct(value.schema());
        for (Field field : value.schema().fields()) {
            logger.trace("Denullifying " + field.name());
            final Object origFieldValue = value.get(field);
            updatedValue.put(field, maskedFields.contains(field.name()) ? masked(origFieldValue) : origFieldValue);
        }
        return newRecord(record, updatedValue);
    }

    protected static Object masked(Object value) {
        Object maskedValue = null;

        if (value == null) {
            maskedValue = PRIMITIVE_VALUE_MAPPING.get(value.getClass());
            if (maskedValue == null) {
                if (value instanceof List)
                    maskedValue = Collections.emptyList();
                else if (value instanceof Map)
                    maskedValue = Collections.emptyMap();
                else
                    throw new DataException("Cannot denullify value of type: " + value.getClass());
            }
        }

        return maskedValue;
    }

    @Override
    public ConfigDef config() {
        return CONFIG_DEF;
    }

    @Override
    public void close() {
    }

    protected Schema operatingSchema(R record) {
        return record.valueSchema();
    }

    protected Object operatingValue(R record) {
        return record.value();
    }

    protected R newRecord(R record, Object updatedValue) {
        return record.newRecord(record.topic(), record.kafkaPartition(), record.keySchema(), record.key(), record.valueSchema(), updatedValue, record.timestamp());
    }
}
