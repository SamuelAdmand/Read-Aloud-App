import asyncio
import edge_tts
import json

async def _tts(text, voice, output_file):
    """
    Generates audio from text and saves it to the specified file.
    """
    communicate = edge_tts.Communicate(text, voice)
    await communicate.save(output_file)
    return output_file

async def _get_voices():
    """
    Fetches the list of available voices from edge-tts.
    """
    voices = await edge_tts.list_voices()
    return json.dumps(voices)

def get_voices_json():
    """
    Synchronous entry point for Kotlin to call.
    Runs the async edge-tts function in a new event loop.
    """
    loop = asyncio.new_event_loop()
    asyncio.set_event_loop(loop)
    try:
        result = loop.run_until_complete(_get_voices())
        return result
    finally:
        loop.close()

def tts(text, voice, output_file):
    """
    Synchronous entry point for TTS generation.
    """
    loop = asyncio.new_event_loop()
    asyncio.set_event_loop(loop)
    try:
        return loop.run_until_complete(_tts(text, voice, output_file))
    finally:
        loop.close()