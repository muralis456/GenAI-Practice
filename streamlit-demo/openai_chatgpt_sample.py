from openai import OpenAI
from dotenv import load_dotenv
import time

# Load environment variables from .env file
load_dotenv()

# Initialize the OpenAI client
# Make sure to set your API key as an environment variable: OPENAI_API_KEY
client = OpenAI()

# Retry wrapper with exponential backoff for rate limits
def invoke_with_retry(messages, max_attempts=3):
    for attempt in range(max_attempts):
        try:
            print(f"Attempting to create chat completion (attempt {attempt+1}/{max_attempts})...")
            response = client.chat.completions.create(
                model="gpt-3.5-turbo",
                messages=messages
            )
            print("Success!")
            return response
        except Exception as e:
            msg = str(e).lower()
            print(f"Error encountered: {e}")
            if "429" in msg or "rate limit" in msg or "quota" in msg:
                if attempt < max_attempts - 1:
                    wait_time = 2 ** attempt
                    print(f"Rate limited. Retrying in {wait_time}s (attempt {attempt+1}/{max_attempts})")
                    time.sleep(wait_time)
                    continue
                else:
                    print("Rate limit exceeded. Please try again later.")
                    return None
            else:
                print(f"Error: {e}")
                return None
    return None

# Create a chat completion with retry logic
response = invoke_with_retry([
    {"role": "user", "content": "Write a short bedtime story about a unicorn."}
])

# Print the response
if response:
    print(response.choices[0].message.content)
