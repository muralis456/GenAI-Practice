import streamlit as st
from langchain_openai import ChatOpenAI

# Use a currently available Chat Completions model.
# If this still fails, replace with any other model
# you see enabled in your OpenAI dashboard (e.g. gpt-4.1).
llm = ChatOpenAI(model="gpt-4.1-mini", temperature=0)

st.title("LangChain + OpenAI Chat Demo")

user_input = st.text_input("Ask something to GPT‑4:", "Hello, how are you?")

if st.button("Send"):
    with st.spinner("Thinking..."):
        response = llm.invoke(user_input)
        st.write(response.content)