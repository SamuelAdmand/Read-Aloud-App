import json
import trafilatura
from trafilatura.settings import use_config

def extract_from_url(url):
    """
    Downloads an article from a URL and extracts the main text.
    Returns a JSON string with 'title' and 'text', or an error.
    """
    try:
        # standard config, but we can customize if needed
        config = use_config()

        # 1. Download the HTML
        # We use a custom User-Agent to mimic a Google Bot.
        # Many soft paywalls allow bots to index their content.
        downloaded = trafilatura.fetch_url(url)

        if downloaded is None:
            # If standard fetch fails, try again with explicit headers using requests
            # (trafilatura does this internally but sometimes needs forcing)
            # For now, we rely on trafilatura's internal robust fetcher.
            return json.dumps({"error": "Failed to download page. Check your internet or the URL."})

        # 2. Extract content
        # include_comments=False -> skip comments
        # include_tables=False -> skip tables (better for TTS flow)
        result = trafilatura.extract(
            downloaded,
            include_comments=False,
            include_tables=False,
            include_images=False,
            output_format='json',
            with_metadata=True
        )

        if result:
            # trafilatura returns a JSON string when output_format='json'
            # keys: 'title', 'text', 'date', 'author', 'fingerprint', 'url', 'license'
            return result
        else:
            return json.dumps({"error": "Could not extract text from this page."})

    except Exception as e:
        return json.dumps({"error": str(e)})