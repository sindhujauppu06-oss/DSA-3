# Read text for Rabin-Karp
with open("sample.txt", "r") as file:
    text = file.read().strip()

pattern = input("Enter Pattern: ")

print("\nRabin-Karp Result")
print("-----------------")


# Simple Rabin-Karp using Python hash
m = len(pattern)
pattern_hash = hash(pattern)

for i in range(len(text) - m + 1):
    window = text[i:i + m]

    if hash(window) == pattern_hash:
        if window == pattern:
            print("Pattern found at index", i)


# Document Similarity
doc1 = open("doc1.txt", "r").read().lower().split()
doc2 = open("doc2.txt", "r").read().lower().split()

common_suffix_words = set(doc1).intersection(set(doc2))

print("\nCommon Words")
print(common_suffix_words)

similarity = (
    len(common_suffix_words)
    / len(set(doc1).union(set(doc2)))
) * 100

print("Similarity = {:.2f}%".format(similarity))