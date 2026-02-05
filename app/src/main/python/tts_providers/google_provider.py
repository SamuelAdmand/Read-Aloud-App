import json
from gtts import gTTS, lang
from tts_providers.base_provider import BaseTTSProvider


class GoogleTTSProvider(BaseTTSProvider):
    def __init__(self, log_func):
        self.log_d = log_func

    def get_voices(self):
        langs = lang.tts_langs()
        voice_list = []
        for code, name in langs.items():
            voice_list.append({"ShortName": code, "FriendlyName": name, "Locale": code})
        voice_list.sort(key=lambda x: x["FriendlyName"])
        return json.dumps(voice_list)

    def tts(self, text, voice, output_file):
        self.log_d(f"Starting Google TTS for: {text[:20]}... using lang: {voice}")
        tts = gTTS(text=text, lang=voice)
        tts.save(output_file)

        safe_text = text.replace("\n", " ").replace("\r", "")
        srt_file = output_file + ".srt"
        with open(srt_file, "w", encoding="utf-8") as file:
            file.write("1\n00:00:00,000 --> 00:59:59,999\n" + safe_text)

        return output_file
