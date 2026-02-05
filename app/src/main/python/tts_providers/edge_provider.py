import asyncio
import edge_tts
import json
from tts_providers.base_provider import BaseTTSProvider


class EdgeTTSProvider(BaseTTSProvider):
    def __init__(self, log_func):
        self.log_d = log_func

    def get_voices(self):
        loop = asyncio.new_event_loop()
        asyncio.set_event_loop(loop)
        try:
            voices = loop.run_until_complete(edge_tts.list_voices())
            return json.dumps(voices)
        finally:
            loop.close()

    def tts(self, text, voice, output_file):
        loop = asyncio.new_event_loop()
        asyncio.set_event_loop(loop)
        try:
            return loop.run_until_complete(self._edge_tts(text, voice, output_file))
        finally:
            loop.close()

    async def _edge_tts(self, text, voice, output_file):
        self.log_d(f"Starting Edge TTS for: {text[:20]}... using voice: {voice}")
        communicate = edge_tts.Communicate(text, voice)
        submaker = edge_tts.SubMaker()

        audio_chunks = 0
        boundary_events = 0

        with open(output_file, "wb") as file:
            async for chunk in communicate.stream():
                if chunk["type"] == "audio":
                    file.write(chunk["data"])
                    audio_chunks += 1
                elif "offset" in chunk and "duration" in chunk:
                    submaker.feed(chunk)
                    boundary_events += 1
                elif (
                    boundary_events == 0
                    and audio_chunks < 5
                    and chunk["type"] != "audio"
                ):
                    self.log_d(f"Ignored Chunk Type: {chunk.get('type')}")

        self.log_d(
            f"Stream finished. Audio: {audio_chunks}, Boundaries: {boundary_events}"
        )

        if boundary_events > 0:
            try:
                srt_content = submaker.get_srt()
                srt_file = output_file + ".srt"
                with open(srt_file, "w", encoding="utf-8") as file:
                    file.write(srt_content)
            except Exception as e:
                self.log_d(f"Error generating SRT: {e}")

        return output_file
