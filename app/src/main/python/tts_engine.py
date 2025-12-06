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
    log_d(f"Starting TTS (Fast Mode) for: {len(text)} chars")
    communicate = edge_tts.Communicate(text, voice)
    await communicate.save(output_file)
    log_d("Saved audio.")
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