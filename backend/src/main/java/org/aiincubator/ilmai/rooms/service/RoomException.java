package org.aiincubator.ilmai.rooms.service;

import lombok.Getter;

@Getter
public class RoomException extends RuntimeException {

    @Getter
    public enum Reason {
        ROOM_NOT_FOUND("room.error.notFound"),
        NAME_BLANK("room.error.nameBlank"),
        NOT_A_MEMBER("room.error.notMember"),
        NOT_OWNER("room.error.notOwner"),
        INVALID_INVITE("room.error.invalidInvite"),
        MEMBER_NOT_FOUND("room.error.memberNotFound"),
        OWNER_CANNOT_LEAVE("room.error.ownerCannotLeave"),
        CANNOT_REMOVE_OWNER("room.error.cannotRemoveOwner"),
        PREMIUM_REQUIRED("room.error.premiumRequired"),
        INVALID_TARGET_DATE("profile.error.invalidTargetDate");

        private final String messageKey;

        Reason(String messageKey) {
            this.messageKey = messageKey;
        }
    }

    private final Reason reason;
    private final Object[] messageArgs;

    public RoomException(Reason reason) {
        this(reason, new Object[0]);
    }

    public RoomException(Reason reason, Object... messageArgs) {
        super(reason.messageKey);
        this.reason = reason;
        this.messageArgs = messageArgs == null ? new Object[0] : messageArgs;
    }
}
