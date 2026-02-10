from langchain_openai import ChatOpenAI
llm = ChatOpenAI(model="gpt-4", temperature=0)
response = llm.chat("Hello, how are you?")
print(response.content)  # Print the response from the model