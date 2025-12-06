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
        return _extract_generic(url)
    except Exception as e:
        return json.dumps({"error": str(e)})

def _extract_generic(url):
    """
    Uses Trafilatura to download and extract text as Markdown.
    """
    try:
        config = use_config()
        config.set("DEFAULT", "USER_AGENT", "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:109.0) Gecko/20100101 Firefox/115.0")

        downloaded = trafilatura.fetch_url(url)

        if downloaded is None:
            return json.dumps({"error": "Failed to download page. Check your internet or the URL."})

        # 1. Extract Metadata (Title, etc.)
        metadata = trafilatura.extract_metadata(downloaded)
        title = metadata.title if metadata and metadata.title else "No Title"

        # 2. Extract Content as Markdown
        # We enable formatting and tables to get the structure the user wants.
        content = trafilatura.extract(
            downloaded,
            include_comments=False,
            include_tables=True,
            include_images=False,
            include_formatting=True,
            output_format='markdown'
        )

        if content:
            return json.dumps({
                "title": title,
                "text": content,
                "url": url
            })
        else:
            return json.dumps({"error": "Could not extract text from this page."})

    except Exception as e:
        return json.dumps({"error": str(e)})