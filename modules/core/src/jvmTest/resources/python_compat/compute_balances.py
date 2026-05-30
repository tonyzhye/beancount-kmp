#!/usr/bin/env python3
"""
Python Beancount balance computation wrapper for compatibility testing.

This script loads a beancount file, computes account balances using
realization, and outputs the results as JSON.

Usage:
    python compute_balances.py <file_path>
    python compute_balances.py --string <beancount_content>
"""

import json
import sys
import os
from datetime import date
from decimal import Decimal

from beancount import loader
from beancount.core import realization
from beancount.core.amount import Amount


def serialize_inventory(inventory):
    """Convert an Inventory to a list of position dicts."""
    positions = []
    for position in inventory.get_positions():
        if position.units.number != 0:
            positions.append({
                "number": str(position.units.number),
                "currency": position.units.currency,
                "cost": serialize_cost(position.cost) if position.cost else None
            })
    return positions


def serialize_cost(cost):
    """Convert a Cost to a dict."""
    if cost is None:
        return None
    return {
        "number": str(cost.number),
        "currency": cost.currency,
        "date": cost.date.isoformat() if cost.date else None,
        "label": cost.label,
    }


def compute_balances(filepath=None, content=None):
    """Load beancount and compute account balances."""
    if content is not None:
        entries, errors, options = loader.load_string(content)
    else:
        entries, errors, options = loader.load_file(filepath)
    
    # Realize accounts
    root_account = realization.realize(entries)
    
    # Collect all account balances
    account_balances = {}
    for real_account in realization.iter_children(root_account):
        if real_account.account:  # Skip root
            balance = realization.compute_balance(real_account)
            account_balances[real_account.account] = serialize_inventory(balance)
    
    return {
        "entry_count": len(entries),
        "error_count": len(errors),
        "accounts": list(account_balances.keys()),
        "balances": account_balances,
        "errors": [{"message": e.message, "source": str(e.source)} for e in errors[:10]],
    }


def main():
    if len(sys.argv) < 2:
        print("Usage: python compute_balances.py <file_path>", file=sys.stderr)
        print("       python compute_balances.py --string <beancount_content>", file=sys.stderr)
        sys.exit(1)
    
    if sys.argv[1] == "--string":
        if len(sys.argv) < 3:
            print("Error: --string requires content", file=sys.stderr)
            sys.exit(1)
        result = compute_balances(content=sys.argv[2])
    else:
        filepath = sys.argv[1]
        if not os.path.exists(filepath):
            print(f"Error: File not found: {filepath}", file=sys.stderr)
            sys.exit(1)
        result = compute_balances(filepath=filepath)
    
    print(json.dumps(result, indent=2))


if __name__ == "__main__":
    main()
