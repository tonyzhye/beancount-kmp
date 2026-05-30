#!/usr/bin/env python3
"""
Generate large beancount test files for performance benchmarking.

Usage:
    python generate_large_ledger.py --size 1MB --output test_1mb.beancount
    python generate_large_ledger.py --size 10MB --output test_10mb.beancount
    python generate_large_ledger.py --size 100MB --output test_100mb.beancount
"""

import argparse
import random
from datetime import date, timedelta


def generate_ledger(target_size_mb, output_file):
    """Generate a beancount ledger file of approximately target_size_mb."""
    target_bytes = target_size_mb * 1024 * 1024
    
    # Estimate bytes per transaction (~200 bytes for a typical transaction)
    bytes_per_txn = 250
    num_transactions = int(target_bytes // bytes_per_txn)
    
    print(f"Generating {target_size_mb}MB ledger (~{num_transactions:,} transactions)...")
    
    accounts = [
        "Assets:Bank:Checking",
        "Assets:Bank:Savings",
        "Assets:Investments:Stocks",
        "Assets:Investments:Bonds",
        "Assets:RealEstate:Home",
        "Liabilities:CreditCard:Visa",
        "Liabilities:CreditCard:Mastercard",
        "Liabilities:Mortgage",
        "Liabilities:StudentLoan",
        "Income:Salary",
        "Income:Freelance",
        "Income:Investments:Dividends",
        "Income:Investments:Interest",
        "Expenses:Food:Groceries",
        "Expenses:Food:Restaurant",
        "Expenses:Transport:Gas",
        "Expenses:Transport:PublicTransit",
        "Expenses:Housing:Rent",
        "Expenses:Housing:Utilities",
        "Expenses:Healthcare:Insurance",
        "Expenses:Healthcare:Doctor",
        "Expenses:Entertainment:Movies",
        "Expenses:Entertainment:Games",
        "Expenses:Shopping:Clothing",
        "Expenses:Shopping:Electronics",
        "Equity:OpeningBalances",
    ]
    
    currencies = ["USD", "EUR", "GBP", "JPY", "CAD"]
    payees = [
        "Grocery Store", "Gas Station", "Restaurant", "Pharmacy",
        "Electric Company", "Internet Provider", "Landlord",
        "Employer", "Client A", "Client B", "Investment Broker",
        "Doctor", "Movie Theater", "Online Store", "Coffee Shop",
        "Supermarket", "Hardware Store", "Bookstore", "Gym",
    ]
    narrations = [
        "Weekly shopping", "Fill up tank", "Dinner with friends",
        "Monthly bill", "Quarterly dividend", "Salary payment",
        "Doctor visit", "New equipment", "Coffee break",
        "Gym membership", "Online purchase", "Utility bill",
        "Rent payment", "Investment purchase", "Stock dividend",
    ]
    
    rng = random.Random(42)  # Fixed seed for reproducibility
    
    with open(output_file, 'w') as f:
        # Write header
        f.write("option \"title\" \"Performance Test Ledger\"\n")
        f.write("option \"operating_currency\" \"USD\"\n\n")
        
        # Open all accounts
        start_date = date(2020, 1, 1)
        for account in accounts:
            f.write(f"{start_date} open {account}\n")
        f.write("\n")
        
        # Generate transactions
        current_date = start_date
        for i in range(num_transactions):
            # Advance date occasionally
            if i % 10 == 0:
                current_date += timedelta(days=rng.randint(1, 3))
            
            # Select random accounts (one expense/asset, one income/liability)
            expense_account = rng.choice([a for a in accounts if a.startswith(("Expenses:", "Assets:"))])
            income_account = rng.choice([a for a in accounts if a.startswith(("Income:", "Liabilities:", "Equity:"))])
            
            amount = round(rng.uniform(1.0, 1000.0), 2)
            currency = rng.choice(currencies)
            payee = rng.choice(payees)
            narration = rng.choice(narrations)
            
            f.write(f"{current_date} * \"{payee}\" \"{narration}\"\n")
            f.write(f"  {expense_account}  {amount:.2f} {currency}\n")
            f.write(f"  {income_account}\n\n")
            
            if i % 10000 == 0 and i > 0:
                print(f"  Generated {i:,} transactions...")
    
    actual_size = output_file.stat().st_size
    print(f"Done! Generated {num_transactions:,} transactions")
    print(f"File size: {actual_size / (1024*1024):.2f} MB")
    return num_transactions


def main():
    parser = argparse.ArgumentParser(description="Generate large beancount test files")
    parser.add_argument("--size", type=float, required=True, help="Target size in MB")
    parser.add_argument("--output", type=str, required=True, help="Output file path")
    args = parser.parse_args()
    
    from pathlib import Path
    generate_ledger(args.size, Path(args.output))


if __name__ == "__main__":
    main()
