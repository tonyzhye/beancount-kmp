#!/usr/bin/env python3
"""
Enhanced Python beancount parse benchmark script with memory measurement.

Usage:
    python benchmark_parse_enhanced.py <file_path> [--iterations 3] [--measure-memory]
"""

import sys
import time
import os
import json
import tracemalloc
import gc
from beancount import loader


def benchmark_parse(filepath, iterations=3, measure_memory=True):
    """Benchmark beancount file parsing with optional memory measurement."""
    file_size = os.path.getsize(filepath)
    
    times = []
    entry_counts = []
    error_counts = []
    memory_mb = []
    
    for i in range(iterations):
        # Force GC before measurement
        gc.collect()
        
        if measure_memory:
            tracemalloc.start()
            tracemalloc.reset_peak()
        
        start = time.perf_counter()
        entries, errors, options = loader.load_file(filepath)
        elapsed = time.perf_counter() - start
        
        if measure_memory:
            current, peak = tracemalloc.get_traced_memory()
            tracemalloc.stop()
            memory_mb.append(peak / (1024 * 1024))
        else:
            memory_mb.append(0)
        
        times.append(elapsed)
        entry_counts.append(len(entries))
        error_counts.append(len(errors))
        
        mem_str = f", {memory_mb[-1]:.1f}MB" if measure_memory else ""
        print(f"  Iteration {i+1}: {elapsed:.3f}s ({len(entries)} entries, {len(errors)} errors{mem_str})")
    
    avg_time = sum(times) / len(times)
    min_time = min(times)
    max_time = max(times)
    avg_memory = sum(memory_mb) / len(memory_mb) if measure_memory else 0
    max_memory = max(memory_mb) if measure_memory else 0
    
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
        "avg_memory_mb": round(avg_memory, 2),
        "max_memory_mb": round(max_memory, 2),
    }
    
    return result


def main():
    if len(sys.argv) < 2:
        print("Usage: python benchmark_parse_enhanced.py <file_path> [--iterations 3] [--measure-memory]", file=sys.stderr)
        sys.exit(1)
    
    filepath = sys.argv[1]
    iterations = 3
    measure_memory = True
    
    i = 2
    while i < len(sys.argv):
        if sys.argv[i] == "--iterations" and i + 1 < len(sys.argv):
            iterations = int(sys.argv[i + 1])
            i += 2
        elif sys.argv[i] == "--measure-memory":
            measure_memory = True
            i += 1
        elif sys.argv[i] == "--no-memory":
            measure_memory = False
            i += 1
        else:
            i += 1
    
    if not os.path.exists(filepath):
        print(f"Error: File not found: {filepath}", file=sys.stderr)
        sys.exit(1)
    
    print(f"Benchmarking Python beancount parsing: {filepath}")
    result = benchmark_parse(filepath, iterations, measure_memory)
    
    print("\nResults:")
    print(f"  Average time: {result['avg_time_sec']:.3f}s")
    print(f"  Throughput: {result['throughput_entries_per_sec']:,.1f} entries/sec")
    print(f"  Throughput: {result['throughput_mb_per_sec']:.2f} MB/sec")
    if measure_memory:
        print(f"  Avg memory: {result['avg_memory_mb']:.2f} MB")
        print(f"  Max memory: {result['max_memory_mb']:.2f} MB")
    
    # Output JSON result on a single line prefixed with JSON_RESULT:
    print(f"JSON_RESULT:{json.dumps(result)}")


if __name__ == "__main__":
    main()
