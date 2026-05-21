package ru.university.textbot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.longpolling.TelegramBotsLongPollingApplication;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import ru.university.textbot.config.BotConfig;

public class TextBotApplication {

    private static final Logger logger = LoggerFactory.getLogger(TextBotApplication.class);

    public static void main(String[] args) {
        System.setProperty("socksProxyHost", "68.209.62.102");
        System.setProperty("socksProxyPort", "8000");
        System.setProperty("java.net.socks.username", "BQvnjM");
        System.setProperty("java.net.socks.password", "Az9dt8");

        TextProcessorBot bot = new TextProcessorBot();

        try (TelegramBotsLongPollingApplication app = new TelegramBotsLongPollingApplication()) {

            app.registerBot(BotConfig.BOT_TOKEN, bot);

            logger.info("✅ Бот успешно запущен!");
            logger.info("🤖 Имя бота: @{}", BotConfig.BOT_USERNAME);
            logger.info("🎮 Режим: Викторина (Синонимы/Антонимы)");
            logger.info("💡 Команды: /start, /quiz, /stop, /stats");
            logger.info("🌐 Прокси: 68.209.62.102:8000 (SOCKS)");
            logger.info("Для остановки нажмите Ctrl+C");

            Thread.currentThread().join();

        } catch (TelegramApiException e) {
            logger.error("❌ Ошибка Telegram API: {}", e.getMessage(), e);
        } catch (InterruptedException e) {
            logger.info("👋 Бот остановлен");
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            logger.error("❌ Неожиданная ошибка: {}", e.getMessage(), e);
        }
    }
}