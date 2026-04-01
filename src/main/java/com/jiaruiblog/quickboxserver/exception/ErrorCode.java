package com.jiaruiblog.quickboxserver.exception;


import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


public enum ErrorCode {

    SUCCESS(2000, "error-code.success"),

    // HTTP Status Codes
    NOT_FOUND(404, "error-code.not-found"),
    INVALID_PARAM(400, "error-code.bad-request"),
    UNAUTHORIZED(401, "error-code.unauthorized"),
    FORBIDDEN(403, "error-code.forbidden"),
    INTERNAL_ERROR(500, "error-code.internal-server-error"),

    // User Related
    USER_NOT_FOUND(1001, "error-code.user-not-found"),
    INVALID_CREDENTIALS(1002, "error-code.invalid-credentials"),
    EMAIL_EXISTS(1003, "error-code.email-already-exists"),
    USERNAME_EXISTS(1004, "error-code.username-already-exists"),
    USER_DISABLED(1005, "error-code.user-disabled"),
    ACCOUNT_LOCKED(1006, "error-code.account-locked"),
    PASSWORD_EXPIRED(1007, "error-code.password-expired"),
    INVALID_PASSWORD(1008, "error-code.invalid-password"),

    // Common Error Codes (merged from MessageConstant)
    PARAMS_ERROR(1201, "error-code.params-error"),
    PROCESS_ERROR(1202, "error-code.process-error"),
    OPERATE_FAILED(1203, "error-code.operate-failed"),
    DATA_IS_EMPTY(1204, "error-code.data-is-empty"),

    // File Related
    FILE_NOT_FOUND(2001, "error-code.file-not-found"),
    FILE_UPLOAD_FAILED(2002, "error-code.file-upload-failed"),
    FILE_DOWNLOAD_FAILED(2003, "error-code.file-download-failed"),
    FILE_DELETE_FAILED(2004, "error-code.file-delete-failed"),
    FILE_SIZE_EXCEEDED(2005, "error-code.file-size-exceeded"),
    INVALID_FILE_TYPE(2006, "error-code.invalid-file-type"),
    INVALID_FILE_NAME(2007, "error-code.invalid-file-name"),
    STORAGE_QUOTA_EXCEEDED(2008, "error-code.storage-quota-exceeded"),

    // Document Related
    DOCUMENT_NOT_FOUND(3001, "error-code.document-not-found"),
    DOCUMENT_UPDATE_FAILED(3002, "error-code.document-update-failed"),
    DOCUMENT_DELETE_FAILED(3003, "error-code.document-delete-failed"),
    DOCUMENT_VERSION_CONFLICT(3004, "error-code.document-version-conflict"),
    DOCUMENT_ALREADY_EXISTS(3005, "error-code.document-already-exists"),

    // Team Related
    TEAM_NOT_FOUND(4001, "error-code.team-not-found"),
    TEAM_MEMBER_EXISTS(4002, "error-code.team-member-exists"),
    TEAM_MEMBER_NOT_FOUND(4003, "error-code.team-member-not-found"),
    TEAM_QUOTA_EXCEEDED(4004, "error-code.team-quota-exceeded"),

    // Organization Related
    ORGANIZATION_NOT_FOUND(5001, "error-code.organization-not-found"),
    ORGANIZATION_MEMBER_EXISTS(5002, "error-code.organization-member-exists"),
    ORGANIZATION_MEMBER_NOT_FOUND(5003, "error-code.organization-member-not-found"),

    // Permission Related
    PERMISSION_DENIED(6001, "error-code.permission-denied"),
    INVALID_PERMISSION(6002, "error-code.invalid-permission"),
    ROLE_NOT_FOUND(6003, "error-code.role-not-found"),

    // Category & Tag Related
    CATEGORY_NOT_FOUND(7001, "error-code.category-not-found"),
    TAG_NOT_FOUND(7002, "error-code.tag-not-found"),
    CATEGORY_EXISTS(7003, "error-code.category-exists"),
    TAG_EXISTS(7004, "error-code.tag-exists"),

    // Comment Related
    COMMENT_NOT_FOUND(8001, "error-code.comment-not-found"),
    COMMENT_DELETE_FAILED(8002, "error-code.comment-delete-failed"),

    // System Configuration
    CONFIG_NOT_FOUND(9001, "error-code.config-not-found"),
    CONFIG_UPDATE_FAILED(9002, "error-code.config-update-failed"),

    // Upload Session Related
    SESSION_NOT_FOUND(10001, "error-code.session-not-found"),
    SESSION_EXPIRED(10002, "error-code.session-expired"),
    SESSION_INVALID_STATE(10003, "error-code.session-invalid-state"),
    SESSION_ALREADY_COMPLETED(10004, "error-code.session-already-completed"),

    // Folder Related
    FOLDER_NOT_FOUND(11001, "error-code.folder-not-found"),
    FOLDER_UPLOAD_INIT_FAILED(11002, "error-code.folder-upload-init-failed"),
    FOLDER_CHUNK_UPLOAD_FAILED(11003, "error-code.folder-chunk-upload-failed"),
    FOLDER_MERGE_FAILED(11004, "error-code.folder-merge-failed"),
    FOLDER_CANCEL_FAILED(11005, "error-code.folder-cancel-failed"),
    FOLDER_INFO_FAILED(11006, "error-code.folder-info-failed"),
    FOLDER_DOWNLOAD_FAILED(11007, "error-code.folder-download-failed"),
    FOLDER_ALREADY_DOWNLOADED(11008, "error-code.folder-already-downloaded"),
    FOLDER_EXPIRED(11009, "error-code.folder-expired"),
    FOLDER_DELETE_FAILED(11010, "error-code.folder-delete-failed"),
    FOLDER_CLEANUP_FAILED(11014, "error-code.folder-cleanup-failed"),
    INVALID_FOLDER_NAME(11011, "error-code.invalid-folder-name"),
    FOLDER_SIZE_EXCEEDED(11012, "error-code.folder-size-exceeded"),
    FOLDER_FILE_COUNT_EXCEEDED(11013, "error-code.folder-file-count-exceeded"),

    // Storage Related
    STORAGE_SERVICE_NOT_FOUND(12001, "error-code.storage-service-not-found"),
    STORAGE_SERVICE_UNAVAILABLE(12002, "error-code.storage-service-unavailable"),
    STORAGE_CONFIG_ERROR(12003, "error-code.storage-config-error"),
    STORAGE_OPERATION_FAILED(12004, "error-code.storage-operation-failed"),
    STORAGE_SPACE_INSUFFICIENT(12005, "error-code.storage-space-insufficient"),

    // ZIP Related
    ZIP_CREATION_FAILED(13001, "error-code.zip-creation-failed"),
    ZIP_EXTRACTION_FAILED(13002, "error-code.zip-extraction-failed"),
    INVALID_ZIP_FORMAT(13003, "error-code.invalid-zip-format"),
    ZIP_SIZE_EXCEEDED(13004, "error-code.zip-size-exceeded"),

    // Storage Init Related
    STORAGE_INIT_FAILED(14001, "error-code.storage-init-failed"),
    STORAGE_INIT_LOCAL_FAILED(14002, "error-code.storage-init-local-failed"),
    STORAGE_INIT_S3_FAILED(14003, "error-code.storage-init-s3-failed"),

    // S3 Related
    S3_CLIENT_BUILD_FAILED(14101, "error-code.s3-client-build-failed"),
    S3_ENDPOINT_INVALID(14102, "error-code.s3-endpoint-invalid"),
    S3_PRESIGNER_BUILD_FAILED(14103, "error-code.s3-presigner-build-failed"),
    S3_CHUNK_UPLOAD_FAILED(14104, "error-code.s3-chunk-upload-failed"),
    S3_FILE_MERGE_FAILED(14105, "error-code.s3-file-merge-failed"),
    S3_FILE_DOWNLOAD_FAILED(14106, "error-code.s3-file-download-failed"),
    S3_METADATA_GET_FAILED(14107, "error-code.s3-metadata-get-failed"),
    S3_FILE_DELETE_FAILED(14108, "error-code.s3-file-delete-failed"),
    S3_FILE_NOT_EXISTS(14109, "error-code.s3-file-not-exists"),
    S3_FILE_INFO_GET_FAILED(14110, "error-code.s3-file-info-get-failed"),
    S3_FILE_EXISTS_CHECK_FAILED(14111, "error-code.s3-file-exists-check-failed"),
    S3_FOLDER_CHUNK_UPLOAD_FAILED(14112, "error-code.s3-folder-chunk-upload-failed"),
    S3_FOLDER_MERGE_FAILED(14113, "error-code.s3-folder-merge-failed"),
    S3_FOLDER_DOWNLOAD_FAILED(14114, "error-code.s3-folder-download-failed"),
    S3_FOLDER_NOT_EXISTS(14115, "error-code.s3-folder-not-exists"),
    S3_FOLDER_METADATA_NOT_EXISTS(14116, "error-code.s3-folder-metadata-not-exists"),
    S3_FOLDER_INFO_GET_FAILED(14117, "error-code.s3-folder-info-get-failed"),
    S3_FOLDER_EXISTS_CHECK_FAILED(14118, "error-code.s3-folder-exists-check-failed"),

    // Local Storage Related
    LOCAL_DIRECTORY_CREATE_FAILED(14201, "error-code.local-directory-create-failed"),
    LOCAL_FILE_UPLOAD_INIT_FAILED(14202, "error-code.local-file-upload-init-failed"),
    LOCAL_CHUNK_UPLOAD_FAILED(14203, "error-code.local-chunk-upload-failed"),
    LOCAL_FILE_MERGE_FAILED(14204, "error-code.local-file-merge-failed"),
    LOCAL_FILE_DOWNLOAD_FAILED(14205, "error-code.local-file-download-failed"),
    LOCAL_FILE_DELETE_FAILED(14206, "error-code.local-file-delete-failed"),
    LOCAL_FILE_INFO_GET_FAILED(14207, "error-code.local-file-info-get-failed"),
    LOCAL_FOLDER_UPLOAD_INIT_FAILED(14208, "error-code.local-folder-upload-init-failed"),
    LOCAL_FOLDER_CHUNK_UPLOAD_FAILED(14209, "error-code.local-folder-chunk-upload-failed"),
    LOCAL_FOLDER_MERGE_FAILED(14210, "error-code.local-folder-merge-failed"),
    LOCAL_FOLDER_DOWNLOAD_FAILED(14211, "error-code.local-folder-download-failed"),
    LOCAL_FOLDER_INFO_GET_FAILED(14212, "error-code.local-folder-info-get-failed"),

    // Session/Cleanup Related
    SESSION_NOT_EXISTS(14301, "error-code.session-not-exists"),
    FILE_CLEANUP_FAILED(14302, "error-code.file-cleanup-failed"),
    STORAGE_DIRECTORY_INIT_FAILED(14303, "error-code.storage-directory-init-failed"),

    // Storage Config Related
    STORAGE_CONFIG_EMPTY(14401, "error-code.storage-config-empty"),
    STORAGE_SERVICE_NOT_ENABLED(14402, "error-code.storage-service-not-enabled"),
    STORAGE_TYPE_NOT_SUPPORTED(14403, "error-code.storage-type-not-supported"),
    STORAGE_SERVICE_ALREADY_EXISTS(14404, "error-code.storage-service-already-exists"),
    STORAGE_SERVICE_EMPTY(14405, "error-code.storage-service-empty"),
    PRIMARY_STORAGE_NOT_SET(14406, "error-code.primary-storage-not-set"),
    UPLOAD_SESSION_NOT_EXISTS(14407, "error-code.upload-session-not-exists"),

    // File Related
    FILE_NOT_EXISTS(14501, "error-code.file-not-exists"),
    INVALID_ACCESS_CODE(14502, "error-code.invalid-access-code"),
    NO_FILE_FOUND_FOR_ACCESS_CODE(14503, "error-code.no-file-found-for-access-code"),
    FOLDER_NOT_FOUND_FOR_ACCESS_CODE(14504, "error-code.folder-not-found-for-access-code"),
    NOT_A_FOLDER_UPLOAD(14505, "error-code.not-a-folder-upload"),
    INVALID_CHUNK_FILENAME_FORMAT(14506, "error-code.invalid-chunk-filename-format"),
    CHUNK_COUNT_MISMATCH(14507, "error-code.chunk-count-mismatch"),
    FILE_SIZE_MISMATCH(14508, "error-code.file-size-mismatch"),

    // WebDAV Related
    WEBDAV_NOT_IMPLEMENTED(14601, "error-code.webdav-not-implemented"),

    // Operation Not Supported
    OPERATION_NOT_SUPPORTED(14701, "error-code.operation-not-supported"),
    FILE_PATH_EMPTY(14702, "error-code.file-path-empty"),
    FILE_PATH_TOO_LONG(14703, "error-code.file-path-too-long"),
    FOLDER_PATH_EMPTY(14704, "error-code.folder-path-empty"),
    FOLDER_PATH_TOO_LONG(14705, "error-code.folder-path-too-long"),
    UNKNOWN_STORAGE_TYPE(14706, "error-code.unknown-storage-type"),

    // Model Validation Related
    UPLOAD_SESSION_ID_EMPTY(14801, "error-code.upload-session-id-empty"),
    CHUNK_NUMBER_INVALID(14802, "error-code.chunk-number-invalid"),
    TOTAL_CHUNKS_MUST_POSITIVE(14803, "error-code.total-chunks-must-positive"),
    CHUNK_NUMBER_EXCEEDS_TOTAL(14804, "error-code.chunk-number-exceeds-total"),
    CHUNK_SIZE_MUST_POSITIVE(14805, "error-code.chunk-size-must-positive"),
    FILE_NAME_EMPTY(14806, "error-code.file-name-empty"),
    CHUNK_FILE_EMPTY(14807, "error-code.chunk-file-empty"),
    RELATIVE_PATH_EMPTY(14808, "error-code.relative-path-empty"),
    FOLDER_NAME_EMPTY(14809, "error-code.folder-name-empty"),
    TOTAL_FILES_MUST_POSITIVE(14810, "error-code.total-files-must-positive"),
    TOTAL_SIZE_MUST_POSITIVE(14811, "error-code.total-size-must-positive"),
    EXPIRE_TIME_TOO_SHORT(14812, "error-code.expire-time-too-short"),

    // Locale Change Not Allowed
    LOCALE_CHANGE_NOT_ALLOWED(14901, "error-code.locale-change-not-allowed");


    private final Integer code;
    private final String messageKey;

    ErrorCode(Integer code, String messageKey) {
        this.code = code;
        this.messageKey = messageKey;
    }

    public Integer getCode() {
        return code;
    }

    public String getMessageKey() {
        return messageKey;
    }

    public String getLocalizedMessage(MessageSource messageSource, Locale locale) {
        Locale targetLocale = (locale != null) ? locale : LocaleContextHolder.getLocale();
        return messageSource.getMessage(
                this.messageKey,
                null,
                targetLocale
        );
    }

    private static Map<Locale, Map<ErrorCode, String>> cache = new ConcurrentHashMap<>();

    public String getMessage(MessageSource messageSource, Locale locale, Object... args) {
        return cache.computeIfAbsent(locale, l -> new ConcurrentHashMap<>())
                .computeIfAbsent(this,
                        ec -> messageSource.getMessage(
                                this.messageKey,
                                args,
                                this.messageKey,
                                locale));
    }
}