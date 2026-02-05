import json
from java import jclass
from tts_providers.edge_provider import EdgeTTSProvider
from tts_providers.google_provider import GoogleTTSProvider

Log = jclass("android.util.Log")
TAG = "python_tts"


def log_d(msg):
    Log.d(TAG, str(msg))


# --- Provider Factory ---


def _get_provider(provider_name):
    """
    Returns the appropriate TTS provider instance.
    """
    if provider_name == "google":
        return GoogleTTSProvider(log_d)

    # Default to Edge
    return EdgeTTSProvider(log_d)


# --- Public Interface ---


def get_voices_json(provider="edge"):
    """
    Called from Kotlin to get the list of voices for a specific provider.
    """
    try:
        provider_instance = _get_provider(provider)
        return provider_instance.get_voices()
    except Exception as e:
        log_d(f"Error getting voices for {provider}: {e}")
        return json.dumps([])


def tts(text, voice, output_file, provider="edge"):
    """
    Called from Kotlin to generate TTS audio.
    """
    try:
        provider_instance = _get_provider(provider)
        return provider_instance.tts(text, voice, output_file)
    except Exception as e:
        log_d(f"Error generating TTS for {provider}: {e}")
        return ""
