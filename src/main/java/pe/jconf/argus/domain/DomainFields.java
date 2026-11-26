package pe.jconf.argus.domain;

final class DomainFields {
    private DomainFields() {
    }

    static String requiredText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value;
    }
}
