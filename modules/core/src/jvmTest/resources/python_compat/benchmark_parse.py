#!/usr/bin/env python3
"""
Python beancount parse benchmark script.

Usage:
    python benchmark_parse.py <file_path> [--iterations 3]
"""

import sys
import time
import os
import json
from beancount import loader


def benchmark_parse(filepath, iterations=3):
    """Benchmark beancount file parsing."""
    file_size = os.path.getsize(filepath)
    
    times = []
    entry_counts = []
    error_counts = []
    
    for i in range(iterations):
        start = time.perf_counter()
        entries, errors, options = loader.load_file(filepath)
        elapsed = time.perf_counter() - start
        
        times.append(elapsed)
        entry_counts.append(len(entries))
        error_counts.append(len(errors))
        
        print(f"  Iteration {i+1}: {elapsed:.3f}s ({len(entries)} entries, {len(errors)} errors)")
    
    avg_time = sum(times) / len(times)
    min_time = min(times)
    max_time = max(times)
    
    throughput = entry_counts[0] / avg_time if avg_time > 0 else 0
    throughput_mb = (file_size / (1024*1024)) / avg_time if avg_time > 0 else 0
    
    result = {
        "language": "Python",
        "file_size_bytes": file_size,
        "file_size_mb": round(file_size / (1024*1024), 2),
        "iterations": iterations,
        "avg_time_sec": round(avg_time, 3),
        "min_time_sec": round(min_time, 3),
        "max_time_sec": round(max_time, 3),
        "entries": entry_counts[0],
        "errors": error_counts[0],
        "throughput_entries_per_sec": round(throughput, 1),
        "throughput_mb_per_sec": round(throughput_mb, 2),
    }
    
    return result


def main():
    if len(sys.argv) < 2:
        print("Usage: python benchmark_parse.py <file_path> [--iterations 3]", file=sys.stderr)
        sys.exit(1)
    
    filepath = sys.argv[1]
    iterations = int(sys.argv[3]) if len(sys.argv) > 3 and sys.argv[2] == "--iterations" else 3
    
    if not os.path.exists(filepath):
        print(f"Error: File not found: {filepath}", file=sys.stderr)
        sys.exit(1)
    
    print(f"Benchmarking Python beancount parsing: {filepath}")
    result = benchmark_parse(filepath, iterations)
    
    print("\nResults:")
    print(f"  Average time: {result['avg_time_sec']:.3f}s")
    print(f"  Throughput: {result['throughput_entries_per_sec']:,.1f} entries/sec")
    print(f"  Throughput: {result['throughput_mb_per_sec']:.2f} MB/sec")
    
    # Output JSON result on a single line prefixed with JSON:
    print(f"JSON_RESULT:{json.dumps(result)}")


if __name__ == "__main__":
    main()
