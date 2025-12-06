import json
import trafilatura
from trafilatura.settings import use_config
from urllib.parse import urlparse

def extract_from_url(url):
    """
    Router function that decides which extractor to use based on the URL.
    """
    try:
        domain = urlparse(url).netloc.lower()

        # --- SITE SPECIFIC RULES ---
        # Example: if "reddit.com" in domain: return _extract_reddit(url)
        # You can add specific `elif` blocks here for sites that need special handling.

        # --- GENERIC FALLBACK ---
        return _extract_generic(url)

    except Exception as e:
        return json.dumps({"error": str(e)})

def _extract_generic(url):
    """
    Uses Trafilatura to download and extract text.
    """
    try:
        config = use_config()
        # Set user-agent to avoid being blocked
        config.set("DEFAULT", "USER_AGENT", "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:109.0) Gecko/20100101 Firefox/115.0")

        downloaded = trafilatura.fetch_url(url)

        if downloaded is None:
            return json.dumps({"error": "Failed to download page. Check your internet or the URL."})

        result = trafilatura.extract(
            downloaded,
            include_comments=False,
            include_tables=False,
            include_images=False,
            output_format='json',
            with_metadata=True
        )

        if result:
            return result
        else:
            return json.dumps({"error": "Could not extract text from this page."})

    except Exception as e:
        return json.dumps({"error": str(e)})