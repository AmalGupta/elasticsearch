/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */
package org.elasticsearch.xpack.sql.action;

import org.elasticsearch.Version;
import org.elasticsearch.action.ActionRequestValidationException;
import org.elasticsearch.xcontent.ParseField;
import org.elasticsearch.common.Strings;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.core.TimeValue;
import org.elasticsearch.xcontent.ObjectParser;
import org.elasticsearch.xcontent.XContentBuilder;
import org.elasticsearch.xcontent.XContentParser;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.tasks.Task;
import org.elasticsearch.tasks.TaskId;
import org.elasticsearch.xpack.sql.proto.Protocol;
import org.elasticsearch.xpack.sql.proto.RequestInfo;
import org.elasticsearch.xpack.sql.proto.SqlTypedParamValue;

import java.io.IOException;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.elasticsearch.action.ValidateActions.addValidationError;
import static org.elasticsearch.xpack.sql.proto.Protocol.BINARY_FORMAT_NAME;
import static org.elasticsearch.xpack.sql.proto.Protocol.COLUMNAR_NAME;
import static org.elasticsearch.xpack.sql.proto.Protocol.DEFAULT_KEEP_ALIVE;
import static org.elasticsearch.xpack.sql.proto.Protocol.DEFAULT_KEEP_ON_COMPLETION;
import static org.elasticsearch.xpack.sql.proto.Protocol.DEFAULT_WAIT_FOR_COMPLETION_TIMEOUT;
import static org.elasticsearch.xpack.sql.proto.Protocol.FIELD_MULTI_VALUE_LENIENCY_NAME;
import static org.elasticsearch.xpack.sql.proto.Protocol.INDEX_INCLUDE_FROZEN_NAME;
import static org.elasticsearch.xpack.sql.proto.Protocol.KEEP_ALIVE_NAME;
import static org.elasticsearch.xpack.sql.proto.Protocol.KEEP_ON_COMPLETION_NAME;
import static org.elasticsearch.xpack.sql.proto.Protocol.MIN_KEEP_ALIVE;
import static org.elasticsearch.xpack.sql.proto.Protocol.WAIT_FOR_COMPLETION_TIMEOUT_NAME;

/**
 * Request to perform an sql query
 */
public class SqlQueryRequest extends AbstractSqlQueryRequest {
    private static final ObjectParser<SqlQueryRequest, Void> PARSER = objectParser(SqlQueryRequest::new);
    static final ParseField COLUMNAR = new ParseField(COLUMNAR_NAME);
    static final ParseField FIELD_MULTI_VALUE_LENIENCY = new ParseField(FIELD_MULTI_VALUE_LENIENCY_NAME);
    static final ParseField INDEX_INCLUDE_FROZEN = new ParseField(INDEX_INCLUDE_FROZEN_NAME);
    static final ParseField BINARY_COMMUNICATION = new ParseField(BINARY_FORMAT_NAME);
    static final ParseField WAIT_FOR_COMPLETION_TIMEOUT = new ParseField(WAIT_FOR_COMPLETION_TIMEOUT_NAME);
    static final ParseField KEEP_ON_COMPLETION = new ParseField(KEEP_ON_COMPLETION_NAME);
    static final ParseField KEEP_ALIVE = new ParseField(KEEP_ALIVE_NAME);

    static {
        PARSER.declareString(SqlQueryRequest::cursor, CURSOR);
        PARSER.declareBoolean(SqlQueryRequest::columnar, COLUMNAR);
        PARSER.declareBoolean(SqlQueryRequest::fieldMultiValueLeniency, FIELD_MULTI_VALUE_LENIENCY);
        PARSER.declareBoolean(SqlQueryRequest::indexIncludeFrozen, INDEX_INCLUDE_FROZEN);
        PARSER.declareBoolean(SqlQueryRequest::binaryCommunication, BINARY_COMMUNICATION);
        PARSER.declareField(SqlQueryRequest::waitForCompletionTimeout,
            (p, c) -> TimeValue.parseTimeValue(p.text(), WAIT_FOR_COMPLETION_TIMEOUT_NAME), WAIT_FOR_COMPLETION_TIMEOUT,
            ObjectParser.ValueType.VALUE);
        PARSER.declareBoolean(SqlQueryRequest::keepOnCompletion, KEEP_ON_COMPLETION);
        PARSER.declareField(SqlQueryRequest::keepAlive,
            (p, c) -> TimeValue.parseTimeValue(p.text(), KEEP_ALIVE_NAME), KEEP_ALIVE, ObjectParser.ValueType.VALUE);
    }

    private String cursor = "";
    /*
     * Using the Boolean object here so that SqlTranslateRequest to set this to null (since it doesn't need a "columnar" or
     * binary parameter).
     * See {@code SqlTranslateRequest.toXContent}
     */
    private Boolean columnar = Protocol.COLUMNAR;
    private Boolean binaryCommunication = Protocol.BINARY_COMMUNICATION;

    private boolean fieldMultiValueLeniency = Protocol.FIELD_MULTI_VALUE_LENIENCY;
    private boolean indexIncludeFrozen = Protocol.INDEX_INCLUDE_FROZEN;

    // Async settings
    private TimeValue waitForCompletionTimeout = DEFAULT_WAIT_FOR_COMPLETION_TIMEOUT;
    private boolean keepOnCompletion = DEFAULT_KEEP_ON_COMPLETION;
    private TimeValue keepAlive = DEFAULT_KEEP_ALIVE;

    public SqlQueryRequest() {
        super();
    }

    public SqlQueryRequest(String query, List<SqlTypedParamValue> params, QueryBuilder filter, Map<String, Object> runtimeMappings,
                           ZoneId zoneId, String catalog, int fetchSize, TimeValue requestTimeout, TimeValue pageTimeout, Boolean columnar,
                           String cursor, RequestInfo requestInfo, boolean fieldMultiValueLeniency, boolean indexIncludeFrozen,
                           TimeValue waitForCompletionTimeout, boolean keepOnCompletion, TimeValue keepAlive) {
        super(query, params, filter, runtimeMappings, zoneId, catalog, fetchSize, requestTimeout, pageTimeout, requestInfo);
        this.cursor = cursor;
        this.columnar = columnar;
        this.fieldMultiValueLeniency = fieldMultiValueLeniency;
        this.indexIncludeFrozen = indexIncludeFrozen;
        this.waitForCompletionTimeout = waitForCompletionTimeout;
        this.keepOnCompletion = keepOnCompletion;
        this.keepAlive = keepAlive;
    }

    @Override
    public ActionRequestValidationException validate() {
        ActionRequestValidationException validationException = super.validate();
        if (Strings.hasText(query()) == false && Strings.hasText(cursor) == false) {
            validationException = addValidationError("one of [query] or [cursor] is required", validationException);
        }
        return validationException;
    }

    /**
     * The key that must be sent back to SQL to access the next page of
     * results.
     */
    public String cursor() {
        return cursor;
    }

    /**
     * The key that must be sent back to SQL to access the next page of
     * results.
     */
    public SqlQueryRequest cursor(String cursor) {
        if (cursor == null) {
            throw new IllegalArgumentException("cursor may not be null.");
        }
        this.cursor = cursor;
        return this;
    }

    /**
     * Should format the values in a columnar fashion or not (default false).
     * Depending on the format used (csv, tsv, txt, json etc) this setting will be taken into
     * consideration or not, depending on whether it even makes sense for that specific format or not.
     */
    public Boolean columnar() {
        return columnar;
    }

    public SqlQueryRequest columnar(boolean columnar) {
        this.columnar = columnar;
        return this;
    }

    public SqlQueryRequest fieldMultiValueLeniency(boolean leniency) {
        this.fieldMultiValueLeniency = leniency;
        return this;
    }

    public boolean fieldMultiValueLeniency() {
        return fieldMultiValueLeniency;
    }

    public SqlQueryRequest indexIncludeFrozen(boolean include) {
        this.indexIncludeFrozen = include;
        return this;
    }

    public boolean indexIncludeFrozen() {
        return indexIncludeFrozen;
    }

    public SqlQueryRequest binaryCommunication(boolean binaryCommunication) {
        this.binaryCommunication = binaryCommunication;
        return this;
    }

    public Boolean binaryCommunication() {
        return binaryCommunication;
    }

    public SqlQueryRequest waitForCompletionTimeout(TimeValue waitForCompletionTimeout) {
        this.waitForCompletionTimeout = waitForCompletionTimeout;
        return this;
    }

    public TimeValue waitForCompletionTimeout() {
        return waitForCompletionTimeout;
    }

    public SqlQueryRequest keepOnCompletion(boolean keepOnCompletion) {
        this.keepOnCompletion = keepOnCompletion;
        return this;
    }

    public boolean keepOnCompletion() {
        return keepOnCompletion;
    }

    public SqlQueryRequest keepAlive(TimeValue keepAlive) {
        if (keepAlive != null && keepAlive.getMillis() < MIN_KEEP_ALIVE.getMillis()) {
            throw new IllegalArgumentException("[" + KEEP_ALIVE_NAME + "] must be greater than " + MIN_KEEP_ALIVE + ", got: " + keepAlive);
        }
        this.keepAlive = keepAlive;
        return this;
    }

    public TimeValue keepAlive() {
        return keepAlive;
    }

    @Override
    public Task createTask(long id, String type, String action, TaskId parentTaskId, Map<String, String> headers) {
        return new SqlQueryTask(id, type, action, getDescription(), parentTaskId, headers, null, null, keepAlive,
            mode(), version(), columnar());
    }

    public SqlQueryRequest(StreamInput in) throws IOException {
        super(in);
        cursor = in.readString();
        columnar = in.readOptionalBoolean();
        fieldMultiValueLeniency = in.readBoolean();
        indexIncludeFrozen = in.readBoolean();
        binaryCommunication = in.readOptionalBoolean();
        if (in.getVersion().onOrAfter(Version.V_8_0_0)) { // TODO: V_7_14_0
            this.waitForCompletionTimeout = in.readOptionalTimeValue();
            this.keepOnCompletion = in.readBoolean();
            this.keepAlive = in.readOptionalTimeValue();
        }
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        out.writeString(cursor);
        out.writeOptionalBoolean(columnar);
        out.writeBoolean(fieldMultiValueLeniency);
        out.writeBoolean(indexIncludeFrozen);
        out.writeOptionalBoolean(binaryCommunication);
        if (out.getVersion().onOrAfter(Version.V_8_0_0)) { // TODO: V_7_14_0
            out.writeOptionalTimeValue(waitForCompletionTimeout);
            out.writeBoolean(keepOnCompletion);
            out.writeOptionalTimeValue(keepAlive);
        }
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), cursor, columnar, fieldMultiValueLeniency, indexIncludeFrozen, binaryCommunication,
            waitForCompletionTimeout, keepOnCompletion, keepAlive);
    }

    @Override
    public boolean equals(Object obj) {
        return super.equals(obj)
                && Objects.equals(cursor, ((SqlQueryRequest) obj).cursor)
                && Objects.equals(columnar, ((SqlQueryRequest) obj).columnar)
                && fieldMultiValueLeniency == ((SqlQueryRequest) obj).fieldMultiValueLeniency
                && indexIncludeFrozen == ((SqlQueryRequest) obj).indexIncludeFrozen
                && binaryCommunication == ((SqlQueryRequest) obj).binaryCommunication
                && Objects.equals(waitForCompletionTimeout, ((SqlQueryRequest) obj).waitForCompletionTimeout)
                && keepOnCompletion == ((SqlQueryRequest) obj).keepOnCompletion
                && Objects.equals(keepAlive, ((SqlQueryRequest) obj).keepAlive);
    }

    @Override
    public String getDescription() {
        return "SQL [" + query() + "][" + filter() + "]";
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        // This is needed just to test round-trip compatibility with proto.SqlQueryRequest
        return new org.elasticsearch.xpack.sql.proto.SqlQueryRequest(query(), params(), zoneId(), catalog(), fetchSize(), requestTimeout(),
                pageTimeout(), filter(), columnar(), cursor(), requestInfo(), fieldMultiValueLeniency(), indexIncludeFrozen(),
                binaryCommunication(), runtimeMappings(), waitForCompletionTimeout(), keepOnCompletion(), keepAlive())
            .toXContent(builder, params);
    }

    public static SqlQueryRequest fromXContent(XContentParser parser) {
        SqlQueryRequest request = PARSER.apply(parser,  null);
        validateParams(request.params(), request.mode());
        return request;
    }
}
