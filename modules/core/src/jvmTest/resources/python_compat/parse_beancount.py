#!/usr/bin/env python3
"""
Python Beancount parser wrapper for compatibility testing.

This script parses a beancount file or string and outputs the results
as JSON for comparison with the Kotlin implementation.

Usage:
    python parse_beancount.py <file_path>
    python parse_beancount.py --string <beancount_content>
"""

import json
import sys
import os
from datetime import date
from decimal import Decimal

from beancount import loader
from beancount.core import data
from beancount.core.amount import Amount
from beancount.core.position import Cost, CostSpec
from beancount.parser import printer


def directive_to_dict(entry):
    """Convert a Beancount directive to a dictionary."""
    if entry is None:
        return None
    
    result = {
        "type": entry.__class__.__name__,
        "date": entry.date.isoformat(),
        "meta": serialize_value({
            k: v for k, v in entry.meta.items()
            if k not in ("filename", "lineno")
        }),
    }
    
    if isinstance(entry, data.Open):
        result.update({
            "account": entry.account,
            "currencies": list(entry.currencies or []),
            "booking": entry.booking.name if entry.booking else None,
        })
    elif isinstance(entry, data.Close):
        result.update({
            "account": entry.account,
        })
    elif isinstance(entry, data.Commodity):
        result.update({
            "currency": entry.currency,
        })
    elif isinstance(entry, data.Balance):
        result.update({
            "account": entry.account,
            "amount": amount_to_dict(entry.amount),
            "tolerance": str(entry.tolerance) if entry.tolerance else None,
            "diff_amount": amount_to_dict(entry.diff_amount) if entry.diff_amount else None,
        })
    elif isinstance(entry, data.Transaction):
        result.update({
            "flag": entry.flag,
            "payee": entry.payee,
            "narration": entry.narration,
            "tags": sorted(entry.tags or []),
            "links": sorted(entry.links or []),
            "postings": [posting_to_dict(p) for p in entry.postings],
        })
    elif isinstance(entry, data.Note):
        result.update({
            "account": entry.account,
            "comment": entry.comment,
            "tags": sorted(entry.tags or []) if entry.tags else [],
            "links": sorted(entry.links or []) if entry.links else [],
        })
    elif isinstance(entry, data.Document):
        result.update({
            "account": entry.account,
            "filename": entry.filename,
            "tags": sorted(entry.tags or []) if entry.tags else [],
            "links": sorted(entry.links or []) if entry.links else [],
        })
    elif isinstance(entry, data.Price):
        result.update({
            "currency": entry.currency,
            "amount": amount_to_dict(entry.amount),
        })
    elif isinstance(entry, data.Pad):
        result.update({
            "account": entry.account,
            "source_account": entry.source_account,
        })
    elif isinstance(entry, data.Event):
        result.update({
            "type": entry.type,
            "description": entry.description,
        })
    elif isinstance(entry, data.Query):
        result.update({
            "name": entry.name,
            "query": entry.query_string,
        })
    elif isinstance(entry, data.Custom):
        result.update({
            "type": entry.type,
            "values": [custom_value_to_dict(v) for v in entry.values],
        })
    elif isinstance(entry, data.Include):
        result.update({
            "filename": entry.filename,
        })
    
    return result


def posting_to_dict(posting):
    """Convert a posting to a dictionary."""
    result = {
        "account": posting.account,
        "flag": posting.flag,
    }
    if posting.units is not None:
        result["units"] = amount_to_dict(posting.units)
    if posting.cost is not None:
        result["cost"] = cost_to_dict(posting.cost)
    if posting.price is not None:
        result["price"] = amount_to_dict(posting.price)
    if posting.meta:
        result["meta"] = {k: v for k, v in posting.meta.items() 
                         if k not in ("filename", "lineno")}
    return result


def amount_to_dict(amount):
    """Convert an Amount to a dictionary."""
    if amount is None:
        return None
    return {
        "number": str(amount.number),
        "currency": amount.currency,
    }


def cost_to_dict(cost):
    """Convert a Cost or CostSpec to a dictionary."""
    if cost is None:
        return None
    
    if isinstance(cost, Cost):
        return {
            "number": str(cost.number),
            "currency": cost.currency,
            "date": cost.date.isoformat() if cost.date else None,
            "label": cost.label,
        }
    elif isinstance(cost, CostSpec):
        return {
            "number_per": str(cost.number_per) if cost.number_per else None,
            "number_total": str(cost.number_total) if cost.number_total else None,
            "currency": cost.currency,
            "date": cost.date.isoformat() if cost.date else None,
            "label": cost.label,
            "merge": cost.merge,
        }
    return None


def custom_value_to_dict(value):
    """Convert a custom directive value to a dictionary."""
    if isinstance(value, Amount):
        return amount_to_dict(value)
    elif isinstance(value, Decimal):
        return {"type": "Decimal", "value": str(value)}
    elif isinstance(value, date):
        return {"type": "Date", "value": value.isoformat()}
    elif isinstance(value, bool):
        return {"type": "Boolean", "value": value}
    elif isinstance(value, str):
        return {"type": "String", "value": value}
    else:
        return {"type": "Unknown", "value": str(value)}


def error_to_dict(error):
    """Convert an error to a dictionary."""
    return {
        "source": {
            "filename": error.source.get("filename", ""),
            "lineno": error.source.get("lineno", 0),
        },
        "message": error.message,
        "entry_type": error.entry.__class__.__name__ if error.entry else None,
    }


def options_to_dict(options):
    """Convert options map to a dictionary."""
    result = {}
    for key, value in options.items():
        if key in ("dcontext", "input_hash"):
            continue
        result[key] = serialize_value(value)
    return result


def serialize_value(value):
    """Serialize a value to JSON-compatible format."""
    if value is None:
        return None
    elif isinstance(value, Decimal):
        return str(value)
    elif isinstance(value, (list, tuple)):
        return [serialize_value(v) for v in value]
    elif isinstance(value, dict):
        return {k: serialize_value(v) for k, v in value.items()}
    elif isinstance(value, set):
        return sorted(str(v) for v in value)
    elif isinstance(value, date):
        return value.isoformat()
    elif isinstance(value, (int, float, str, bool)):
        return value
    else:
        return str(value)


def parse_file(filepath):
    """Parse a beancount file and return structured results."""
    entries, errors, options = loader.load_file(filepath)
    
    return {
        "entries": [directive_to_dict(e) for e in entries],
        "errors": [error_to_dict(e) for e in errors],
        "options": options_to_dict(options),
        "entry_count": len(entries),
        "error_count": len(errors),
    }


def parse_string(content):
    """Parse a beancount string and return structured results."""
    entries, errors, options = loader.load_string(content)
    
    return {
        "entries": [directive_to_dict(e) for e in entries],
        "errors": [error_to_dict(e) for e in errors],
        "options": options_to_dict(options),
        "entry_count": len(entries),
        "error_count": len(errors),
    }


def main():
    if len(sys.argv) < 2:
        print("Usage: python parse_beancount.py <file_path>", file=sys.stderr)
        print("       python parse_beancount.py --string <beancount_content>", file=sys.stderr)
        sys.exit(1)
    
    if sys.argv[1] == "--string":
        if len(sys.argv) < 3:
            print("Error: --string requires content", file=sys.stderr)
            sys.exit(1)
        content = sys.argv[2]
        result = parse_string(content)
    else:
        filepath = sys.argv[1]
        if not os.path.exists(filepath):
            print(f"Error: File not found: {filepath}", file=sys.stderr)
            sys.exit(1)
        result = parse_file(filepath)
    
    # Output as JSON
    print(json.dumps(result, indent=2))


if __name__ == "__main__":
    main()
