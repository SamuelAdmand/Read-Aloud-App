import json
import trafilatura

def extract_from_html(html_content, url):
    """
    Extracts article content from provided HTML string using Trafilatura.
    """
    try:
        if not html_content:
            return json.dumps({"error": "Empty HTML content provided."})

        # 1. Extract Metadata (Title, etc.) from the HTML string
        metadata = trafilatura.extract_metadata(html_content)
        title = metadata.title if metadata and metadata.title else "No Title"

        # 2. Extract Content as Markdown
        # We pass the 'url' parameter so Trafilatura can resolve relative links (e.g., /image.jpg -> domain.com/image.jpg)
        content = trafilatura.extract(
            html_content,
            url=url,
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