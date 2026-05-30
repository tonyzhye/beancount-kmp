#!/usr/bin/env python3
"""
Python beanquery wrapper for compatibility testing.

This script loads a beancount file and runs BQL queries,
outputting results as JSON.

Usage:
    python run_query.py <file_path> <query_string>
    python run_query.py --string <beancount_content> <query_string>
"""

import json
import sys
import os
from datetime import date
from decimal import Decimal

from beancount import loader
from beanquery import query
from beancount.core.amount import Amount


def serialize_value(value):
    """Serialize a query result value."""
    if value is None:
        return None
    elif isinstance(value, Decimal):
        return str(value)
    elif isinstance(value, date):
        return value.isoformat()
    elif isinstance(value, Amount):
        return str(value)
    elif isinstance(value, (list, tuple)):
        return [serialize_value(v) for v in value]
    elif isinstance(value, dict):
        return {k: serialize_value(v) for k, v in value.items()}
    elif isinstance(value, (int, float, str, bool)):
        return value
    else:
        return str(value)


def run_query(filepath=None, content=None, query_string="SELECT *"):
    """Load beancount and run a BQL query."""
    if content is not None:
        entries, errors, options = loader.load_string(content)
    else:
        entries, errors, options = loader.load_file(filepath)
    
    # Run query
    try:
        result_types, result_rows = query.run_query(entries, options, query_string)
    except Exception as e:
        return {
            "error": str(e),
            "columns": [],
            "rows": [],
        }
    
    # Extract column names and types
    columns = []
    for col in result_types:
        columns.append({
            "name": col.name,
            "type": col.datatype.__name__ if hasattr(col.datatype, "__name__") else str(col.datatype)
        })
    
    # Serialize rows
    rows = []
    for row in result_rows:
        rows.append([serialize_value(v) for v in row])
    
    return {
        "columns": columns,
        "rows": rows,
        "row_count": len(rows),
        "error": None,
    }


def main():
    if len(sys.argv) < 3:
        print("Usage: python run_query.py <file_path> <query_string>", file=sys.stderr)
        print("       python run_query.py --string <content> <query_string>", file=sys.stderr)
        sys.exit(1)
    
    if sys.argv[1] == "--string":
        if len(sys.argv) < 4:
            print("Error: --string requires content and query", file=sys.stderr)
            sys.exit(1)
        result = run_query(content=sys.argv[2], query_string=sys.argv[3])
    else:
        filepath = sys.argv[1]
        query_string = sys.argv[2]
        if not os.path.exists(filepath):
            print(f"Error: File not found: {filepath}", file=sys.stderr)
            sys.exit(1)
        result = run_query(filepath=filepath, query_string=query_string)
    
    print(json.dumps(result, indent=2))


if __name__ == "__main__":
    main()
