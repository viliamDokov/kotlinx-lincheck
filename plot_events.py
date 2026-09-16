#!/usr/bin/env python3
"""
Draw a histogram of the number of events in lincheck executions.

Usage:
  1. Pipe command output directly:
     ./gradlew :integration-test:lincheck:integrationTest --tests "*EConcurrentLinkedQueueTest.testWithEventStructureStrategy*" | python3 plot_events.py

  2. Read from a saved log file:
     python3 plot_events.py output.log

  3. Run the gradle command automatically:
     python3 plot_events.py --run
"""

import sys
import os
import re
import argparse
import subprocess

def parse_events_from_text(text: str):
    # Matches both 'Execution: 200' and 'Exececution: 200' (case-insensitive)
    pattern = re.compile(r"exec(?:e)?cution:\s*(\d+)", re.IGNORECASE)
    return [int(m.group(1)) for m in pattern.finditer(text)]

def print_ascii_histogram(data, bins=15):
    if not data:
        print("No event counts found.")
        return
    min_val, max_val = min(data), max(data)
    total_events = sum(data)
    if min_val == max_val:
        print(f"All values are {min_val} (executions: {len(data)}, total events: {total_events})")
        return
    
    bin_width = (max_val - min_val) / bins
    counts = [0] * bins
    for val in data:
        idx = int((val - min_val) / bin_width)
        if idx >= bins:
            idx = bins - 1
        counts[idx] += 1
    
    max_count = max(counts)
    scale = 40.0 / max_count if max_count > 0 else 1.0

    print("\n--- Event Count Histogram (ASCII) ---")
    print(f"Total Executions: {len(data)} | Total Events: {total_events}")
    for i in range(bins):
        low = min_val + i * bin_width
        high = low + bin_width
        bar = "█" * int(counts[i] * scale)
        print(f"{low:6.1f} - {high:6.1f} | {bar:<40} ({counts[i]})")
    print("--------------------------------------")

def plot_histogram(events, output_file="execution_events.png", show=False):
    try:
        import matplotlib.pyplot as plt
    except ImportError:
        print("Note: matplotlib is not installed. Drawing ASCII histogram instead.")
        print_ascii_histogram(events)
        return

    plt.figure(figsize=(10, 6))
    
    # Calculate appropriate bins
    min_e, max_e = min(events), max(events)
    unique_vals = len(set(events))
    num_bins = min(30, max(10, unique_vals))
    
    counts, bins, patches = plt.hist(
        events,
        bins=num_bins,
        color="#2b7bba",
        edgecolor="black",
        alpha=0.75,
        rwidth=0.9
    )
    
    # Statistics
    total_events = sum(events)
    mean_val = total_events / len(events)
    sorted_events = sorted(events)
    n = len(sorted_events)
    median_val = (sorted_events[n // 2] if n % 2 != 0 
                  else (sorted_events[n // 2 - 1] + sorted_events[n // 2]) / 2)
    
    plt.axvline(mean_val, color='red', linestyle='dashed', linewidth=1.5, label=f'Mean: {mean_val:.1f}')
    plt.axvline(median_val, color='green', linestyle='dotted', linewidth=1.5, label=f'Median: {median_val:.1f}')

    plt.title("Distribution of Number of Events per Lincheck Execution", fontsize=14, fontweight='bold')
    plt.xlabel("Number of Events", fontsize=12)
    plt.ylabel("Execution Count", fontsize=12)
    plt.grid(axis='y', alpha=0.5, linestyle='--')
    plt.legend(loc='upper right', fontsize=11)
    
    # Add summary text box
    stats_text = (f"Total Executions: {len(events)}\n"
                  f"Total Events: {total_events:,}\n"
                  f"Min Events: {min_e}\n"
                  f"Max Events: {max_e}\n"
                  f"Mean: {mean_val:.2f}\n"
                  f"Median: {median_val:.1f}")
    plt.gca().text(
        0.02, 0.95, stats_text,
        transform=plt.gca().transAxes,
        fontsize=10,
        verticalalignment='top',
        bbox=dict(boxstyle='round', facecolor='wheat', alpha=0.5)
    )

    plt.tight_layout()
    plt.savefig(output_file, dpi=300)
    print(f"Histogram saved to: {output_file}")
    
    if show:
        plt.show()

def main():
    parser = argparse.ArgumentParser(description="Plot a histogram of events from Lincheck execution logs.")
    parser.add_argument("input_file", nargs="?", help="Path to log file (optional; defaults to stdin if piped)")
    parser.add_argument("-o", "--output", default="execution_events.png", help="Output image file (default: execution_events.png)")
    parser.add_argument("--show", action="store_true", help="Display the plot in a window (requires GUI environment)")
    parser.add_argument("--run", action="store_true", help="Run the gradle test command directly and plot the result")
    parser.add_argument("--test", default="*EConcurrentLinkedQueueTest.testWithEventStructureStrategy*",
                        help="Test filter pattern when using --run")

    args = parser.parse_args()

    content = ""
    if args.run:
        gradle_cmd = ["./gradlew", ":integration-test:lincheck:integrationTest", "--tests", args.test]
        print(f"Running command: {' '.join(gradle_cmd)}")
        proc = subprocess.run(gradle_cmd, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
        content = proc.stdout
        print(content)
    elif args.input_file:
        with open(args.input_file, "r", encoding="utf-8", errors="replace") as f:
            content = f.read()
    elif not sys.stdin.isatty():
        content = sys.stdin.read()
    else:
        # If neither input file nor piped stdin is given, ask or run default
        print("No input provided via file or pipe. Running gradle command...")
        gradle_cmd = ["./gradlew", ":integration-test:lincheck:integrationTest", "--tests", args.test]
        print(f"Running command: {' '.join(gradle_cmd)}")
        proc = subprocess.run(gradle_cmd, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
        content = proc.stdout
        print(content)

    events = parse_events_from_text(content)
    if not events:
        print("Error: No execution event counts found in the input.", file=sys.stderr)
        sys.exit(1)

    total_events = sum(events)
    print(f"\nParsed {len(events)} execution event counts:")
    print(f"  Total Events: {total_events}")
    print(f"  Min: {min(events)}, Max: {max(events)}, Avg: {total_events/len(events):.2f}")

    plot_histogram(events, output_file=args.output, show=args.show)

if __name__ == "__main__":
    main()
