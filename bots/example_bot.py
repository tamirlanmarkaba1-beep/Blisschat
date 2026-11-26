"""Пример бота. Запуск:  BLISS_BOT_TOKEN="my_bot:TOKEN" python example_bot.py"""
import os
import datetime

from blisschat_bot import Bot

bot = Bot(os.environ["BLISS_BOT_TOKEN"])


@bot.command("start")
def start(ctx):
    ctx.reply("Привет! Я пример бота. Команды: /time, /echo <текст>")


@bot.command("time")
def time_cmd(ctx):
    msk = datetime.datetime.now(datetime.timezone(datetime.timedelta(hours=3)))
    ctx.reply("Сейчас по Москве: " + msk.strftime("%d.%m.%Y %H:%M:%S"))


@bot.command("echo")
def echo(ctx):
    ctx.reply(" ".join(ctx.args) or "Напишите текст после /echo")


@bot.on_message
def anything(ctx):
    if ctx.is_private:
        ctx.reply("Я понимаю команды: /start, /time, /echo")


if __name__ == "__main__":
    bot.run()
