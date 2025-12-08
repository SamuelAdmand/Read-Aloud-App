import asyncio
import edge_tts
import json
import os
from java import jclass
from gtts import gTTS, lang

Log = jclass("android.util.Log")
TAG = "python_tts"

def log_d(msg):
    Log.d(TAG, str(msg))

# --- Edge TTS Logic (Existing) ---

async def _edge_tts(text, voice, output_file):
    log_d(f"Starting Edge TTS for: {text[:20]}... using voice: {voice}")
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
            elif boundary_events == 0 and audio_chunks < 5 and chunk["type"] != "audio":
                 log_d(f"Ignored Chunk Type: {chunk.get('type')}")

    log_d(f"Stream finished. Audio: {audio_chunks}, Boundaries: {boundary_events}")

    if boundary_events > 0:
        try:
            srt_content = submaker.get_srt()
            srt_file = output_file + ".srt"
            with open(srt_file, "w", encoding="utf-8") as file:
                file.write(srt_content)
        except Exception as e:
            log_d(f"Error generating SRT: {e}")

    return output_file

async def _get_edge_voices():
    voices = await edge_tts.list_voices()
    return json.dumps(voices)

# --- Google TTS Logic (New) ---

def _google_tts(text, voice, output_file):
    log_d(f"Starting Google TTS for: {text[:20]}... using lang: {voice}")
    # Voice for gTTS is just the language code (e.g., 'en', 'es')
    # We could extend this to support TLDs for accents later if needed.
    tts = gTTS(text=text, lang=voice)
    tts.save(output_file)

    # Generate dummy SRT because the app expects it for highlighting
    # This prevents the "SRT file not generated" error in Repository
    srt_file = output_file + ".srt"
    with open(srt_file, "w", encoding="utf-8") as file:
        # Create a single subtitle entry spanning a long duration
        file.write("1\n00:00:00,000 --> 00:59:59,999\n" + text)

    return output_file

def _get_google_voices():
    # gTTS exposes languages via tts_langs()
    langs = lang.tts_langs()
    voice_list = []
    for code, name in langs.items():
        voice_list.append({
            "ShortName": code,
            "FriendlyName": name,
            "Locale": code
        })
    # Sort by name
    voice_list.sort(key=lambda x: x["FriendlyName"])
    return json.dumps(voice_list)

# --- Public Interface ---

def get_voices_json(provider="edge"):
    if provider == "google":
        return _get_google_voices()

    # Default to Edge
    loop = asyncio.new_event_loop()
    asyncio.set_event_loop(loop)
    try:
        result = loop.run_until_complete(_get_edge_voices())
        return result
    finally:
        loop.close()

def tts(text, voice, output_file, provider="edge"):
    if provider == "google":
        return _google_tts(text, voice, output_file)

    # Default to Edge
    loop = asyncio.new_event_loop()
    asyncio.set_event_loop(loop)
    try:
        return loop.run_until_complete(_edge_tts(text, voice, output_file))
    finally:
        loop.close()