package org.aiincubator.ilmai.telegram.service;

public enum MenuAction {

    TODAY("telegram.bot.button.today"),
    STREAK("telegram.bot.button.streak"),
    QUIZ("telegram.bot.button.quiz"),
    NEW_CHAT("telegram.bot.button.newchat"),
    CHATS("telegram.bot.button.chats"),
    HELP("telegram.bot.button.help"),
    FORGET("telegram.bot.button.forget");

    final String labelKey;

    MenuAction(String labelKey) {
        this.labelKey = labelKey;
    }
}
