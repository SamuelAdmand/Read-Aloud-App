from abc import ABC, abstractmethod


class BaseTTSProvider(ABC):
    """
    Base class for all TTS providers.
    All methods should be implemented by subclasses.
    """

    @abstractmethod
    def get_voices(self):
        """
        Returns a JSON string containing the available voices.
        """
        pass

    @abstractmethod
    def tts(self, text, voice, output_file):
        """
        Generates audio for the given text and saves it to output_file.
        Returns the path to the output_file.
        """
        pass
