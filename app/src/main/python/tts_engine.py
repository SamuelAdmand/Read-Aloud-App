import asyncio
import edge_tts
import json
import os
from java import jclass

Log = jclass("android.util.Log")
TAG = "python_tts"

def log_d(msg):
    Log.d(TAG, str(msg))

async def _tts(text, voice, output_file):
    log_d(f"Starting TTS for: {text[:20]}... using voice: {voice}")

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
                # FIX: Accept any chunk with timing info, regardless of the specific 'type' label
                submaker.feed(chunk)
                boundary_events += 1
            elif boundary_events == 0 and audio_chunks < 5 and chunk["type"] != "audio":
                 # Log the specific type that failed our previous check, for debugging
                 log_d(f"Ignored Chunk Type: {chunk.get('type')}")

    log_d(f"Stream finished. Audio: {audio_chunks}, Boundaries: {boundary_events}")

    if boundary_events > 0:
        try:
            srt_content = submaker.get_srt()
            srt_file = output_file + ".srt"
            with open(srt_file, "w", encoding="utf-8") as file:
                file.write(srt_content)
            log_d(f"Saved SRT (len={len(srt_content)}) to {srt_file}")
        except Exception as e:
            log_d(f"Error generating SRT: {e}")
    else:
        log_d("No boundaries received. SRT generation skipped.")

    return output_file

async def _get_voices():
    voices = await edge_tts.list_voices()
    return json.dumps(voices)

def get_voices_json():
    loop = asyncio.new_event_loop()
    asyncio.set_event_loop(loop)
    try:
        result = loop.run_until_complete(_get_voices())
        return result
    finally:
        loop.close()

def tts(text, voice, output_file):
    loop = asyncio.new_event_loop()
    asyncio.set_event_loop(loop)
    try:
        return loop.run_until_complete(_tts(text, voice, output_file))
    finally:
        loop.close()