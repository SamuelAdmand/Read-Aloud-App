import asyncio
import edge_tts
import json
import os
from java import jclass

# Access Android Log class to write to Logcat
Log = jclass("android.util.Log")
TAG = "python_tts"

def log_d(msg):
    Log.d(TAG, str(msg))

async def _tts(text, voice, output_file):
    log_d(f"Starting TTS for text length: {len(text)}")
    communicate = edge_tts.Communicate(text, voice)

    timings = []

    with open(output_file, "wb") as file:
        async for chunk in communicate.stream():
            if chunk["type"] == "audio":
                file.write(chunk["data"])
            elif chunk["type"] == "WordBoundary":
                # Collect timing data
                offset = chunk.get("offset", 0)
                duration = chunk.get("duration", 0)
                text_offset = chunk.get("text_offset", 0)
                word_len = chunk.get("word_length", 0)
                word_text = chunk.get("text", "")

                start_seconds = offset / 10_000_000
                duration_seconds = duration / 10_000_000

                timings.append({
                    "word": word_text,
                    "start": start_seconds,
                    "end": start_seconds + duration_seconds,
                    "text_offset": text_offset,
                    "word_len": word_len
                })

    log_d(f"Finished generation. Captured {len(timings)} WordBoundaries.")

    meta_file = output_file + ".json"
    try:
        with open(meta_file, "w") as f:
            json.dump(timings, f)
        log_d(f"Saved timestamps to {meta_file}")
    except Exception as e:
        log_d(f"Failed to save JSON: {e}")

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