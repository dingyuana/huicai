#!/usr/bin/env python3
"""性能回归检查工具 - 对比当前指标与 baseline"""

import json
import sys
import os

def main():
    metrics_file = 'metrics.json'
    base_dir = os.path.dirname(os.path.abspath(__file__))
    baseline_file = os.path.join(base_dir, '..', 'docs', 'testing', 'performance-baseline.json')
    
    if not os.path.exists(baseline_file):
        print("FIRST RUN: Creating baseline...")
        with open(metrics_file) as f:
            current = json.load(f)
        with open(baseline_file, 'w') as f:
            json.dump(current, f, indent=2)
        sys.exit(0)  # OK - no regression to check
    
    with open(metrics_file) as f:
        current = json.load(f)
    with open(baseline_file) as f:
        baseline = json.load(f)
    
    for key in current:
        if key in baseline:
            ratio = current[key] / baseline[key] * 100
            print(f'{key}: baseline={baseline[key]}, current={current[key]}, ratio={ratio:.1f}%')
            if ratio > 120:
                print(f'ERROR: {key} increased by >20%!')
                sys.exit(1)
    
    print('OK: All metrics within acceptable threshold.')
    sys.exit(0)

if __name__ == '__main__':
    main()
