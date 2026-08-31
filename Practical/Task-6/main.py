# -----------------------------------------
# PART A: Sequence Alignment using DP
# -----------------------------------------

def sequence_alignment(seq1, seq2):
    # Get lengths of both sequences
    m = len(seq1)
    n = len(seq2)

    # Create DP table
    dp = [[0 for j in range(n + 1)] for i in range(m + 1)]

    # Initialize first column with gap penalties
    for i in range(m + 1):
        dp[i][0] = -i

    # Initialize first row with gap penalties
    for j in range(n + 1):
        dp[0][j] = -j

    # Fill the DP table
    for i in range(1, m + 1):
        for j in range(1, n + 1):

            # Match = +1, Mismatch = -1
            if seq1[i - 1] == seq2[j - 1]:
                score = 1
            else:
                score = -1

            # Find maximum score
            dp[i][j] = max(
                dp[i - 1][j - 1] + score,
                dp[i - 1][j] - 1,
                dp[i][j - 1] - 1
            )

    return dp[m][n]


print("PART A: SEQUENCE ALIGNMENT")
print("--------------------------")

seq1 = input("Enter Sequence 1: ")
seq2 = input("Enter Sequence 2: ")

result = sequence_alignment(seq1, seq2)

print("Optimal Alignment Score =", result)


# -----------------------------------------
# PART B: Bitmask Dynamic Programming
# -----------------------------------------

print("\nPART B: BITMASK OPTIMIZATION")
print("----------------------------")

values = [10, 20, 30, 40]
max_items = 2

n = len(values)

best_value = 0
best_subset = []

# Generate every possible subset
for mask in range(1 << n):

    total = 0
    selected = []

    # Check every bit
    for i in range(n):

        if mask & (1 << i):
            total += values[i]
            selected.append(values[i])

    # Update best solution
    if len(selected) <= max_items and total > best_value:
        best_value = total
        best_subset = selected


print("Maximum number of items =", max_items)
print("Best selected items =", best_subset)
print("Maximum value =", best_value)