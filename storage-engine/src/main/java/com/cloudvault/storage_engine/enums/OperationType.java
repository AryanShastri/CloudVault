package com.cloudvault.storage_engine.enums;

public enum OperationType {
    PUT(RequestClass.CLASS_A),
    GET(RequestClass.CLASS_B),
    DELETE(RequestClass.FREE),
    LIST(RequestClass.FREE),
    HEAD(RequestClass.CLASS_B),
    COPY(RequestClass.CLASS_A),
    POST(RequestClass.CLASS_A),
    SELECT(RequestClass.CLASS_B);

    private final RequestClass requestClass;

    OperationType(RequestClass requestClass) {
        this.requestClass = requestClass;
    }

    public RequestClass getRequestClass() {
        return requestClass;
    }
}