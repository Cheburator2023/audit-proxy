package ru.vtb.auditproxy.serializer;

import static ru.vtb.omni.audit.lib.api.Constants.HEADER_SCHEMA_TYPE;
import static ru.vtb.omni.audit.lib.api.Constants.HEADER_SCHEMA_VERSION;

import ru.vtb.omni.audit.core.avro.SchemaRepository;
import ru.vtb.omni.audit.lib.api.enums.AudLibEventClass;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericDatumWriter;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.io.DatumWriter;
import org.apache.avro.io.Encoder;
import org.apache.avro.io.EncoderFactory;
import org.apache.commons.lang3.SerializationException;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.serialization.Serializer;

import java.io.ByteArrayOutputStream;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Кастомный сериализатор, устраняющий баг библиотеки tsau-audit-lib,
 * при котором поля типа MAP и RECORD всегда пишутся как null
 * (см. AvroHashMapSerializer в tsau-audit-lib-kafka-sender).
 *
 * Отличия от библиотечного:
 *  - корректно сериализует MAP-поля (additionalParams) в Map&lt;String,String&gt;;
 *  - корректно сериализует ARRAY-поля (event_techCodes) в GenericData.Array;
 *  - RECORD по-прежнему остаётся null (в используемых схемах не встречается).
 */
@Slf4j
@RequiredArgsConstructor
public class AuditMapSerializer implements Serializer<Map<String, Object>> {

    private final SchemaRepository schemaRepository;

    @Override
    public void configure(Map<String, ?> configs, boolean isKey) {
        // nothing to configure
    }

    @Override
    public byte[] serialize(String topic, Map<String, Object> data) {
        // Kafka вызывается только через перегрузку с Headers, поэтому здесь null.
        return null;
    }

    @Override
    public byte[] serialize(String topic, Headers headers, Map<String, Object> data) {
        try {
            Schema actualSchema = schemaRepository.getSchemaObject(
                    new String(headers.lastHeader(HEADER_SCHEMA_TYPE).value()).toLowerCase(Locale.ROOT),
                    Integer.parseInt(new String(headers.lastHeader(HEADER_SCHEMA_VERSION).value())));

            GenericRecord record = new GenericData.Record(actualSchema);

            for (Schema.Field schemaField : actualSchema.getFields()) {
                Schema nonNullSchema = getNonNullSchema(schemaField.schema());
                Object value = data.get(schemaField.name());

                switch (nonNullSchema.getType()) {
                    case MAP -> record.put(schemaField.name(), encodeMap(value));
                    case ARRAY -> record.put(schemaField.name(), encodeArray(value, nonNullSchema));
                    case RECORD -> record.put(schemaField.name(), null);
                    default -> record.put(schemaField.name(), normalizePrimitive(value));
                }
            }

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            DatumWriter<GenericRecord> datumWriter = new GenericDatumWriter<>(actualSchema);
            Encoder encoder = EncoderFactory.get().binaryEncoder(outputStream, null);
            datumWriter.write(record, encoder);
            encoder.flush();
            outputStream.close();

            return outputStream.toByteArray();
        } catch (Exception ex) {
            throw new SerializationException(
                    "Can't serialize message for topic='" + topic + "'", ex);
        }
    }

    /**
     * Приводит значения MAP к строкам — соответствует Avro map&lt;string,string&gt;.
     */
    private Map<String, String> encodeMap(Object value) {
        Map<String, String> result = new HashMap<>();
        if (!(value instanceof Map<?, ?> source) || source.isEmpty()) {
            return result;
        }
        source.forEach((k, v) -> {
            if (k == null) {
                return;
            }
            if (v == null) {
                return;
            }
            result.put(k.toString(), v.toString());
        });
        return result;
    }

    /**
     * Приводит массив к GenericData.Array&lt;String&gt; — соответствует array&lt;string&gt;.
     */
    private GenericData.Array<String> encodeArray(Object value, Schema arraySchema) {
        List<String> tmp = new ArrayList<>();
        if (value instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                if (item != null) {
                    tmp.add(item.toString());
                }
            }
        }
        GenericData.Array<String> array = new GenericData.Array<>(tmp.size(), arraySchema);
        array.addAll(tmp);
        return array;
    }

    /**
     * Нормализует примитивные значения (перечисления, даты, UUID) к Avro-совместимым.
     */
    private Object normalizePrimitive(Object value) {
        if (value instanceof AudLibEventClass) {
            return value.toString();
        }
        if (value instanceof ZonedDateTime) {
            return ((ZonedDateTime) value).toInstant().toEpochMilli();
        }
        if (value instanceof UUID) {
            return value.toString();
        }
        return value;
    }

    private static Schema getNonNullSchema(Schema schema) {
        if (schema.getType() == Schema.Type.UNION) {
            for (Schema type : schema.getTypes()) {
                if (type.getType() != Schema.Type.NULL) {
                    return type;
                }
            }
        }
        return schema;
    }

    @Override
    public void close() {
        // nothing to close
    }
}