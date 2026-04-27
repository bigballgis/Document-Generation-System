# FOL Template Content — Docxtemplater Tags Specification

> 以下为每个 Segment 的 .docx 文件中应包含的 docxtemplater 标签内容。
> 在 OnlyOffice 或 Word 中编辑 .docx 文件时，直接将这些标签文本粘贴到对应位置。

---

## Segment 01: Cover Page (~1 page)

```
                              {%bank_logo}

                    ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

                         {confidentiality_level | upper}

                    ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━


                         FACILITY OFFER LETTER


                    Reference: {document_ref}
                    Date: {document_date | dateFormat:'DD MMMM YYYY'}
                    Version: {document_version | default:'1.0'}


                    FROM:
                    {bank.legal_name | upper}
                    {bank.address.line1}
                    {#if bank.address.line2}{bank.address.line2}{/if}
                    {bank.address.city}, {bank.address.state | default:''} {bank.address.postal_code}
                    {bank.address.country}
                    SWIFT: {bank.swift_code}
                    {#if bank.lei_code}LEI: {bank.lei_code}{/if}
                    License No.: {bank.license_number}


                    TO:
                    {borrower.legal_name | upper}
                    {borrower.registered_address.line1}
                    {#if borrower.registered_address.line2}{borrower.registered_address.line2}{/if}
                    {borrower.registered_address.city}, {borrower.registered_address.state | default:''} {borrower.registered_address.postal_code}
                    {borrower.registered_address.country}
                    Registration No.: {borrower.registration_number}
                    Tax ID: {borrower.tax_id}


                    Total Facility Amount: {base_currency} {total_facility_amount | currency:'':2}

                    Acceptance Deadline: {acceptance_deadline | dateFormat:'DD MMMM YYYY'}
```

---

## Segment 02: Table of Contents (~1 page)

```
                              TABLE OF CONTENTS

Part A    Definitions and Interpretation .......................... 1
Part B    Facility Details ....................................... 9
Part C    Interest and Fees ...................................... 19
Part D    Repayment Schedule ..................................... 27
Part E    Conditions Precedent ................................... 33
Part F    Representations and Warranties ......................... 38
Part G    Covenants .............................................. 44
{#if has_security}Part H    Security and Collateral ................................. 52{/if}
{#if has_guarantor}Part I    Guarantee ............................................... 58{/if}
Part J    Events of Default ...................................... 62
Part K    Governing Law and Jurisdiction ......................... 67
Part L    Miscellaneous .......................................... 70
Appendix A    Form of Compliance Certificate ..................... 73
Appendix B    Form of Drawdown Notice ............................ 75
Appendix C    Fee Schedule ....................................... 77
Signature Page ................................................... 80

Number of Facilities: {facilities | count}
{#if is_syndicated}Syndicate Members: {syndicate_members | count}{/if}
```

---

## Segment 03: Part A — Definitions & Interpretation (~8 pages)

```
PART A — DEFINITIONS AND INTERPRETATION

1. DEFINITIONS

In this Facility Offer Letter, unless the context otherwise requires, the following terms shall
have the meanings set out below:

"Acceptance Deadline" means {acceptance_deadline | dateFormat:'DD MMMM YYYY'}, being
{acceptance_period_days} calendar days from the date of this Letter.

"Agent Bank" means {#if agent_bank}{agent_bank.name}{/if}{#if !agent_bank}{bank.legal_name}{/if},
acting in its capacity as agent for the Lenders.

"Availability Period" means the period from the Effective Date to the date falling
{#facilities}{#if facility_type === 'REVOLVING_CREDIT'}{availability_period_months}{/if}{/facilities}
months after the Effective Date.

"Base Currency" means {base_currency}.

"Borrower" means {borrower.legal_name}, a company incorporated in
{borrower.incorporation_country} on {borrower.incorporation_date | dateFormat:'DD MMMM YYYY'}
with registration number {borrower.registration_number}.

{#if borrower.credit_rating}
"Credit Rating" means the rating of {borrower.credit_rating.rating} assigned by
{borrower.credit_rating.agency} on {borrower.credit_rating.rating_date | dateFormat:'DD MMMM YYYY'}
with outlook {borrower.credit_rating.outlook | default:'Stable'}.
{/if}

"Default Interest Rate" means the applicable Interest Rate plus {default_interest_margin | percent:2}
per annum.

"Effective Date" means {effective_date | dateFormat:'DD MMMM YYYY'}.

"Expiry Date" means {expiry_date | dateFormat:'DD MMMM YYYY'}.

"Facility" or "Facilities" means the credit facility(ies) described in Part B, being:
{#facilities}
  ({facility_name}): {currency} {amount | currency:'':2} — {facility_type | replace:'_':' '}
{/facilities}

"Facility Amount" means the aggregate amount of {base_currency} {total_facility_amount | currency:'':2},
comprising {facilities | count} separate facility(ies).

"Fee Schedule" means the schedule of fees set out in Appendix C, totaling
{base_currency} {total_fee_amount | currency:'':2}.

{#if has_guarantor}
"Guarantor(s)" means:
{#guarantors}
  - {name} ({type | lower} guarantor), providing a {guarantee_type | replace:'_':' ' | lower}
    guarantee of {guarantee_currency} {guarantee_amount | currency:'':2}
{/guarantors}
{/if}

"Interest Period" means each period of one (1), three (3), or six (6) months as selected by
the Borrower, or such other period as agreed between the parties.

"Lender" means {bank.legal_name}.
{#if is_syndicated}
"Lenders" means collectively:
{#syndicate_members | sortBy:'commitment_amount'}
  - {bank_name} ({role | replace:'_':' '}) — {currency} {commitment_amount | currency:'':2}
    ({commitment_percentage | percent:1})
{/syndicate_members}
{/if}

"Material Adverse Change" means any event or circumstance which, in the reasonable opinion
of the Lender, has or could have a material adverse effect on:
(a) the business, operations, property, condition (financial or otherwise) of the Borrower;
(b) the ability of the Borrower to perform its obligations under this Agreement;
(c) the validity or enforceability of this Agreement.
{#if material_adverse_change_threshold}
For the avoidance of doubt, any single event resulting in a loss exceeding
{base_currency} {material_adverse_change_threshold | currency:'':2} shall be deemed a
Material Adverse Change.
{/if}

"Prepayment Notice Period" means {prepayment_notice_days} Business Days.

{#if has_security}
"Security" means the security interests described in Part H, with an aggregate estimated
value of {base_currency} {total_security_value | currency:'':2}, resulting in an overall
loan-to-value ratio of {overall_ltv | percent:1}.
{/if}

"Weighted Average Interest Rate" means {weighted_avg_rate | percent:4} per annum, being the
weighted average of the interest rates applicable to all Facilities.


2. INTERPRETATION

2.1 In this Letter:
    (a) references to "this Letter" include its Appendices;
    (b) headings are for convenience only and shall not affect interpretation;
    (c) words importing the singular include the plural and vice versa;
    (d) references to a "person" include any individual, company, partnership, or other entity;
    (e) references to legislation include any amendment, re-enactment, or replacement thereof;
    (f) references to "Business Day" mean a day (other than Saturday, Sunday, or public holiday)
        on which banks are open for general business in {bank.address.city} and
        {borrower.registered_address.city};
    (g) all monetary amounts are expressed in {base_currency} unless otherwise stated;
    (h) time is of the essence in respect of all obligations under this Letter.

2.2 The Borrower's Industry Sector: {borrower.industry_sector}
    {#if borrower.sic_code}SIC Code: {borrower.sic_code}{/if}

2.3 Authorized Signatories of the Borrower:
{#borrower.authorized_signatories}
    Name: {name}
    Title: {title}
    Email: {email}
    {#if phone}Phone: {phone}{/if}
{/borrower.authorized_signatories}

2.4 Directors of the Borrower:
{#borrower.directors | sortBy:'appointment_date'}
    {name} ({nationality}) — Appointed: {appointment_date | dateFormat:'DD MMM YYYY'}
    {#if is_executive}[Executive Director]{/if}{#if !is_executive}[Non-Executive Director]{/if}
{/borrower.directors}

{#if borrower.shareholders}
2.5 Major Shareholders:
{#borrower.shareholders | sortBy:'percentage' | reverse}
    {name} — {percentage | percent:1} ({country})
    {#if is_ultimate_beneficial_owner}[Ultimate Beneficial Owner]{/if}
{/borrower.shareholders}
{/if}
```

---

## Segment 04: Part B — Facility Details (~10 pages)

```
PART B — FACILITY DETAILS

3. THE FACILITIES

3.1 Subject to the terms and conditions of this Letter, the Lender hereby offers to make
available to the Borrower the following credit facilities:

Summary of Facilities:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Total Number of Facilities: {facilities.$count}
Total Facility Amount: {base_currency} {total_facility_amount | currency:'':2}
Weighted Average Rate: {weighted_avg_rate | percent:4}
Facility Names: {facilities.$join_facility_name}
Minimum Rate: {facilities.$min_interest_rate | percent:2}
Maximum Rate: {facilities.$max_interest_rate | percent:2}
Average Rate: {facilities.$avg_interest_rate | percent:4}
Maximum Tenor: {max_tenor_months} months
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

{#facilities}
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
FACILITY: {facility_name | upper}
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

┌─────────────────────────┬──────────────────────────────────────────────────────┐
│ Facility Type           │ {facility_type | replace:'_':' '}                   │
├─────────────────────────┼──────────────────────────────────────────────────────┤
│ Currency                │ {currency}                                           │
├─────────────────────────┼──────────────────────────────────────────────────────┤
│ Amount                  │ {currency} {amount | currency:'':2}                  │
├─────────────────────────┼──────────────────────────────────────────────────────┤
│ Interest Rate           │ {interest_rate | percent:2} per annum ({rate_type})  │
├─────────────────────────┼──────────────────────────────────────────────────────┤
│ Benchmark Rate          │ {benchmark_rate | default:'N/A'}                     │
├─────────────────────────┼──────────────────────────────────────────────────────┤
│ Spread                  │ {spread | default:0} bps                             │
├─────────────────────────┼──────────────────────────────────────────────────────┤
│ Tenor                   │ {tenor_months} months                                │
├─────────────────────────┼──────────────────────────────────────────────────────┤
│ Availability Period     │ {availability_period_months | default:'N/A'} months  │
├─────────────────────────┼──────────────────────────────────────────────────────┤
│ Drawdown Date           │ {drawdown_date | dateFormat:'DD MMM YYYY' | default:'TBD'} │
├─────────────────────────┼──────────────────────────────────────────────────────┤
│ Maturity Date           │ {maturity_date | dateFormat:'DD MMM YYYY'}           │
├─────────────────────────┼──────────────────────────────────────────────────────┤
│ Purpose                 │ {purpose}                                            │
├─────────────────────────┼──────────────────────────────────────────────────────┤
│ Committed               │ {#if is_committed}Yes{/if}{#if !is_committed}No{/if}│
└─────────────────────────┴──────────────────────────────────────────────────────┘

{#if sub_limits}
Sub-Limits under {facility_name}:
┌────────────────────────────┬──────────────────┬──────────┐
│ Sub-Limit Name             │ Amount           │ Currency │
├────────────────────────────┼──────────────────┼──────────┤
{#sub_limits}
│ {sub_limit_name | padEnd:28} │ {sub_limit_amount | currency:'':2 | padStart:16} │ {sub_limit_currency | padEnd:8} │
{/sub_limits}
├────────────────────────────┼──────────────────┼──────────┤
│ Total Sub-Limits           │ {sub_limits | sumBy:'sub_limit_amount' | currency:'':2} │          │
└────────────────────────────┴──────────────────┴──────────┘
{/if}

{/facilities}

{#if is_syndicated}
3.2 SYNDICATE STRUCTURE

This Facility is arranged on a syndicated basis. The syndicate comprises the following
Lenders:

┌────────────────────────────┬──────────────────┬──────────────┬──────────┬──────────┐
│ Bank Name                  │ Role             │ Commitment   │ Share    │ Currency │
├────────────────────────────┼──────────────────┼──────────────┼──────────┼──────────┤
{#syndicate_members | sortBy:'commitment_percentage' | reverse}
│ {bank_name | padEnd:28} │ {role | replace:'_':' ' | padEnd:16} │ {commitment_amount | currency:'':2 | padStart:12} │ {commitment_percentage | percent:1 | padStart:8} │ {currency | padEnd:8} │
{/syndicate_members}
├────────────────────────────┼──────────────────┼──────────────┼──────────┼──────────┤
│ TOTAL                      │                  │ {syndicate_members | sumBy:'commitment_amount' | currency:'':2} │ 100.0%   │          │
└────────────────────────────┴──────────────────┴──────────────┴──────────┴──────────┘

Agent Bank: {agent_bank.name | default:'N/A'}
{#if agent_bank}
Agent Account: {agent_bank.account_number}
Agent SWIFT: {agent_bank.swift_code}
{#if agent_bank.correspondent_bank}Correspondent Bank: {agent_bank.correspondent_bank}{/if}
{/if}
{/if}

{#if is_revolving}
3.3 REVOLVING FACILITY PROVISIONS

The Borrower may draw down, repay, and re-draw amounts under the Revolving Credit Facility
subject to the following conditions:
(a) Each drawdown shall be in a minimum amount of {base_currency} 100,000;
(b) The aggregate outstanding amount shall not exceed the Facility Amount;
(c) The Borrower shall provide at least 3 Business Days' notice for each drawdown;
(d) Interest shall accrue on drawn amounts at the applicable rate.
{/if}
```

---

## Segment 05: Part C — Interest & Fees (~8 pages)

```
PART C — INTEREST AND FEES

4. INTEREST

4.1 Rate of Interest

The rate of interest applicable to each Facility shall be as follows:

{#facilities}
{facility_name}:
  Base Rate: {interest_rate | percent:2} per annum
  Rate Type: {rate_type}
  {#if rate_type === 'FLOATING'}
  Benchmark: {benchmark_rate}
  Spread: {spread} basis points
  All-in Rate: {benchmark_rate} + {spread} bps
  {/if}
  {#if rate_type === 'FIXED'}
  Fixed Rate: {interest_rate | percent:4} per annum
  {/if}

{/facilities}

4.2 Interest Periods

Interest shall accrue on each Facility during successive Interest Periods. Each Interest
Period shall be one (1), three (3), or six (6) months, as selected by the Borrower in the
relevant Drawdown Notice.

4.3 Default Interest

If the Borrower fails to pay any amount payable by it under this Letter on its due date,
interest shall accrue on the overdue amount from the due date up to the date of actual
payment at a rate which is {default_interest_margin | percent:2} per annum above the
applicable Interest Rate.

4.4 Interest Calculation

Interest shall be calculated on the basis of the actual number of days elapsed and a year
of 360 days (or 365 days for GBP).

4.5 Rate Summary

┌────────────────────────────┬──────────┬──────────┬──────────────┬──────────────┐
│ Facility                   │ Rate     │ Type     │ Benchmark    │ Spread (bps) │
├────────────────────────────┼──────────┼──────────┼──────────────┼──────────────┤
{#facilities | sortBy:'interest_rate'}
│ {facility_name | padEnd:28} │ {interest_rate | percent:2 | padStart:8} │ {rate_type | padEnd:8} │ {benchmark_rate | default:'—' | padEnd:12} │ {spread | default:0 | padStart:12} │
{/facilities}
├────────────────────────────┼──────────┼──────────┼──────────────┼──────────────┤
│ Weighted Average           │ {weighted_avg_rate | percent:4} │          │              │              │
└────────────────────────────┴──────────┴──────────┴──────────────┴──────────────┘


5. FEES

5.1 The Borrower shall pay to the Lender the following fees:

Total Fees: {base_currency} {total_fee_amount | currency:'':2}
Number of Fee Items: {fees | count}

Fee Schedule by Category:

{#fees | groupBy:'category'}
━━━ {key | upper | replace:'_':' '} FEES ━━━
{#items | sortBy:'amount' | reverse}
  • {fee_name}
    Amount: {currency} {amount | currency:'':2}
    Calculation: {calculation_basis | default:'Flat fee'}
    {#if percentage_rate}Rate: {percentage_rate | percent:2}{/if}
    {#if payment_frequency}Frequency: {payment_frequency}{/if}
    {#if due_date}Due: {due_date | dateFormat:'DD MMM YYYY'}{/if}
    Refundable: {#if is_refundable}Yes{/if}{#if !is_refundable}No{/if}
{/items}
Subtotal: {items | sumBy:'amount' | currency:'':2}

{/fees}

5.2 Fee Summary Table

┌────────────────────────────┬──────────┬──────────────┬──────────────┬───────────┐
│ Fee Name                   │ Category │ Amount       │ Basis        │ Refund    │
├────────────────────────────┼──────────┼──────────────┼──────────────┼───────────┤
{#fees | sortBy:'category':'amount'}
│ {fee_name | padEnd:28} │ {category | padEnd:8} │ {currency} {amount | currency:'':2 | padStart:8} │ {calculation_basis | default:'Flat' | padEnd:12} │ {#if is_refundable}Yes{/if}{#if !is_refundable}No{/if}       │
{/fees}
├────────────────────────────┼──────────┼──────────────┼──────────────┼───────────┤
│ TOTAL                      │          │ {fees.$sum_amount | currency:'':2}        │              │           │
└────────────────────────────┴──────────┴──────────────┴──────────────┴───────────┘

Upfront Fees Total: {fees | where:'category === "UPFRONT"' | sumBy:'amount' | currency:'':2}
Recurring Fees Total: {fees | where:'category === "RECURRING"' | sumBy:'amount' | currency:'':2}

5.3 Prepayment

The Borrower may prepay all or part of any Facility by giving not less than
{prepayment_notice_days} Business Days' prior written notice to the Lender.

{#if prepayment_penalty_rate}
A prepayment fee of {prepayment_penalty_rate | percent:2} of the prepaid amount shall be
payable on any voluntary prepayment.
{/if}
```

---

## Segment 06: Part D — Repayment Schedule (~6 pages)

```
PART D — REPAYMENT SCHEDULE

6. REPAYMENT

6.1 The Borrower shall repay each Facility in accordance with the following schedule:

{#facilities}
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
REPAYMENT SCHEDULE — {facility_name | upper}
Facility Amount: {currency} {amount | currency:'':2}
Interest Rate: {interest_rate | percent:2} ({rate_type})
Tenor: {tenor_months} months
Maturity: {maturity_date | dateFormat:'DD MMMM YYYY'}
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

┌────────┬──────────────────┬──────────────────┬──────────────────┬──────────────────┬──────────────────┐
│ Period │ Due Date         │ Principal        │ Interest         │ Total Payment    │ Balance          │
├────────┼──────────────────┼──────────────────┼──────────────────┼──────────────────┼──────────────────┤
{#repayment_schedule}
│ {period | padStart:6} │ {due_date | dateFormat:'DD MMM YYYY' | padEnd:16} │ {principal | currency:'':2 | padStart:16} │ {interest | currency:'':2 | padStart:16} │ {total_payment | currency:'':2 | padStart:16} │ {outstanding_balance | currency:'':2 | padStart:16} │
{/repayment_schedule}
├────────┼──────────────────┼──────────────────┼──────────────────┼──────────────────┼──────────────────┤
│ TOTAL  │                  │ {repayment_schedule | sumBy:'principal' | currency:'':2} │ {repayment_schedule | sumBy:'interest' | currency:'':2} │ {repayment_schedule | sumBy:'total_payment' | currency:'':2} │                  │
└────────┴──────────────────┴──────────────────┴──────────────────┴──────────────────┴──────────────────┘

Number of Payments: {repayment_schedule | count}
Total Principal: {currency} {repayment_schedule | sumBy:'principal' | currency:'':2}
Total Interest: {currency} {repayment_schedule | sumBy:'interest' | currency:'':2}
Total Payments: {currency} {repayment_schedule | sumBy:'total_payment' | currency:'':2}
First Payment: {repayment_schedule | first | dateFormat:'DD MMM YYYY'}
Last Payment: {repayment_schedule | last | dateFormat:'DD MMM YYYY'}

{/facilities}

6.2 Each installment shall be paid on the relevant due date. If a due date falls on a day
which is not a Business Day, payment shall be made on the next Business Day.

6.3 All payments shall be made in the currency of the relevant Facility to the account
specified by the Lender.
```

---

## Segment 07: Part E — Conditions Precedent (~5 pages)

```
PART E — CONDITIONS PRECEDENT

7. CONDITIONS PRECEDENT TO FIRST DRAWDOWN

7.1 The obligation of the Lender to make the first Facility available is subject to the
Lender having received all of the following documents and evidence, in form and substance
satisfactory to the Lender:

Total Conditions: {conditions_precedent | count}

{#conditions_precedent | groupBy:'category'}
━━━ {key | upper} CONDITIONS ━━━

{#items | sortBy:'cp_number'}
{cp_number}. {description}
   Category: {category | replace:'_':' '}
   {#if deadline}Deadline: {deadline | dateFormat:'DD MMM YYYY'}{/if}
   {#if responsible_party}Responsible: {responsible_party}{/if}
   Waivable: {#if is_waivable}Yes{/if}{#if !is_waivable}No{/if}

{/items}
{/conditions_precedent}

7.2 The Lender may, in its sole discretion, waive any Condition Precedent marked as
"Waivable" above.

Waivable Conditions: {conditions_precedent | where:'is_waivable === true' | count}
Non-Waivable Conditions: {conditions_precedent | where:'is_waivable === false' | count}

7.3 All Conditions Precedent must be satisfied or waived by {effective_date | dateFormat:'DD MMMM YYYY'}.
```

---

## Segment 08: Part F — Representations & Warranties (~6 pages)

```
PART F — REPRESENTATIONS AND WARRANTIES

8. REPRESENTATIONS AND WARRANTIES

The Borrower ({borrower.legal_name}) represents and warrants to the Lender that:

Total Representations: {representations | count}
Repeating Representations: {representations | where:'is_repeating === true' | count}

{#representations | sortBy:'rep_number'}
8.{rep_number} {title | upper}

{description}

{#if qualification}
Qualification: {qualification}
{/if}

{#if is_repeating}
[This representation is deemed repeated on each date on which a Facility is drawn down
and on the first day of each Interest Period.]
{/if}

{/representations}

8.{representations | count + 1} GENERAL

The Borrower confirms that:
(a) it is duly incorporated and validly existing under the laws of {borrower.incorporation_country};
(b) it has the power to enter into and perform its obligations under this Letter;
(c) this Letter constitutes its legal, valid, and binding obligations;
(d) no Event of Default is continuing or would result from the proposed drawdown.

{#if borrower.correspondence_address}
All notices to the Borrower shall be sent to:
{borrower.correspondence_address.line1}
{#if borrower.correspondence_address.line2}{borrower.correspondence_address.line2}{/if}
{borrower.correspondence_address.city}, {borrower.correspondence_address.postal_code}
{borrower.correspondence_address.country}
{/if}
```

---

## Segment 09: Part G — Covenants (~8 pages)

```
PART G — COVENANTS

9. FINANCIAL COVENANTS

The Borrower undertakes to comply with the following financial covenants, tested at the
frequency specified:

{#covenants | where:'type === "FINANCIAL"'}
9.{covenant_name}

{description}

┌─────────────────────┬──────────────────────────────────────────┐
│ Threshold           │ {threshold_value | toFixed:2} {threshold_unit | default:''}  │
│ Testing Frequency   │ {testing_frequency | default:'Quarterly'}                    │
│ Cure Period         │ {cure_period_days | default:0} days                          │
│ Material            │ {#if is_material}Yes{/if}{#if !is_material}No{/if}           │
└─────────────────────┴──────────────────────────────────────────┘

{/covenants}


10. INFORMATION COVENANTS

{#covenants | where:'type === "INFORMATION"'}
10.{covenant_name}

{description}
Testing: {testing_frequency | default:'As required'}

{/covenants}

{#if financial_statements}
10.A FINANCIAL REPORTING REQUIREMENTS

┌────────────────────────────┬──────────────┬──────────────┬──────────┐
│ Statement Type             │ Frequency    │ Deadline     │ Audited  │
├────────────────────────────┼──────────────┼──────────────┼──────────┤
{#financial_statements}
│ {statement_type | padEnd:28} │ {frequency | padEnd:12} │ {deadline_days} days     │ {#if requires_audit}Yes{/if}{#if !requires_audit}No{/if}      │
{/financial_statements}
└────────────────────────────┴──────────────┴──────────────┴──────────┘
{/if}


11. POSITIVE COVENANTS

The Borrower undertakes to:

{#covenants | where:'type === "POSITIVE"'}
  • {covenant_name}: {description}
    {#if cure_period_days}(Cure period: {cure_period_days} days){/if}
{/covenants}


12. NEGATIVE COVENANTS

The Borrower undertakes NOT to, without the prior written consent of the Lender:

{#covenants | where:'type === "NEGATIVE"'}
  • {covenant_name}: {description}
{/covenants}


13. GENERAL COVENANTS

{#covenants | where:'type === "GENERAL"'}
  • {covenant_name}: {description}
{/covenants}

Covenant Summary:
  Financial: {covenants | where:'type === "FINANCIAL"' | count}
  Information: {covenants | where:'type === "INFORMATION"' | count}
  Positive: {covenants | where:'type === "POSITIVE"' | count}
  Negative: {covenants | where:'type === "NEGATIVE"' | count}
  General: {covenants | where:'type === "GENERAL"' | count}
  Total: {covenants | count}
  Material: {covenants | where:'is_material === true' | count}

{#if insurance_requirements}
14. INSURANCE

The Borrower shall maintain the following insurance coverage:

┌────────────────────────────┬──────────────────┬──────────┬──────────────┐
│ Insurance Type             │ Min Coverage     │ Currency │ Insurer Rating│
├────────────────────────────┼──────────────────┼──────────┼──────────────┤
{#insurance_requirements}
│ {insurance_type | padEnd:28} │ {minimum_coverage | currency:'':2 | padStart:16} │ {currency | padEnd:8} │ {insurer_rating | default:'N/A' | padEnd:12} │
{/insurance_requirements}
└────────────────────────────┴──────────────────┴──────────┴──────────────┘

Total Minimum Coverage: {insurance_requirements | sumBy:'minimum_coverage' | currency:'':2}
{/if}
```

---

## Segment 10: Part H — Security & Collateral (~6 pages, conditional: has_security)

```
PART H — SECURITY AND COLLATERAL

15. SECURITY

15.1 As continuing security for the payment and discharge of all obligations of the Borrower
under this Letter, the Borrower shall provide the following security:

Total Security Value: {base_currency | default:'USD'} {total_security_value | currency:'':2}
Total Facility Amount: {base_currency | default:'USD'} {total_facility_amount | currency:'':2}
Overall LTV Ratio: {overall_ltv | percent:1}
Number of Security Items: {security_items | count}

{#security_items | sortBy:'estimated_value' | reverse}
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
SECURITY ITEM: {security_type | replace:'_':' ' | upper}
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Description: {description}
Estimated Value: {currency} {estimated_value | currency:'':2}
Valuation Date: {valuation_date | dateFormat:'DD MMMM YYYY'}
{#if valuer_name}Valuer: {valuer_name}{/if}
{#if ltv_ratio}LTV Ratio: {ltv_ratio | percent:1}{/if}

{#if property_details}
Property Details:
  Title Deed: {property_details.title_deed_number | default:'N/A'}
  Location: {property_details.location | default:'N/A'}
  Area: {property_details.area_sqm | default:0} sqm
  Zoning: {property_details.zoning | default:'N/A'}
{/if}

{/security_items}

Security Summary by Type:
{#security_items | groupBy:'security_type'}
  {key | replace:'_':' '}: {items | count} item(s), Value: {items | sumBy:'estimated_value' | currency:'':2}
{/security_items}

15.2 The Borrower shall ensure that:
(a) all security documents are duly executed and registered;
(b) the security remains valid and enforceable at all times;
(c) the aggregate value of the security is maintained at a level satisfactory to the Lender;
(d) periodic valuations are conducted as required by the Lender.
```

---

## Segment 11: Part I — Guarantee (~4 pages, conditional: has_guarantor)

```
PART I — GUARANTEE

16. GUARANTEE

16.1 As further security for the obligations of the Borrower, the following Guarantor(s)
shall provide guarantee(s) in favor of the Lender:

Number of Guarantors: {guarantors | count}
Guarantor Names: {guarantors | joinBy:'name':'; '}
Total Guarantee Amount: {guarantors | sumBy:'guarantee_amount' | currency:'':2}

{#guarantors}
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
GUARANTOR: {name | upper}
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

┌─────────────────────────┬──────────────────────────────────────────────────────┐
│ Guarantor Type          │ {type | replace:'_':' '}                             │
├─────────────────────────┼──────────────────────────────────────────────────────┤
│ Registration No.        │ {registration_number | default:'N/A'}                │
├─────────────────────────┼──────────────────────────────────────────────────────┤
│ Guarantee Amount        │ {guarantee_currency} {guarantee_amount | currency:'':2} │
├─────────────────────────┼──────────────────────────────────────────────────────┤
│ Guarantee Type          │ {guarantee_type | replace:'_':' '}                   │
├─────────────────────────┼──────────────────────────────────────────────────────┤
│ Expiry Date             │ {guarantee_expiry | dateFormat:'DD MMM YYYY' | default:'Co-terminus with Facility'} │
├─────────────────────────┼──────────────────────────────────────────────────────┤
│ Address                 │ {address.line1}, {address.city}, {address.country}   │
└─────────────────────────┴──────────────────────────────────────────────────────┘

{/guarantors}

16.2 Each Guarantor irrevocably and unconditionally guarantees to the Lender the due and
punctual payment and performance of all obligations of the Borrower ({borrower.legal_name})
under this Letter, up to the Total Facility Amount of {base_currency | default:'USD'}
{total_facility_amount | currency:'':2}.

16.3 The guarantee shall be a continuing guarantee and shall remain in full force and effect
until all obligations have been fully and finally discharged.
```

---

## Segment 12: Part J — Events of Default (~5 pages)

```
PART J — EVENTS OF DEFAULT

17. EVENTS OF DEFAULT

Each of the following events or circumstances shall constitute an Event of Default:

Cross Default Threshold: {base_currency} {cross_default_threshold | currency:'':2}
Default Interest Margin: {default_interest_margin | percent:2}

{#events_of_default | sortBy:'eod_number'}
17.{eod_number} {title | upper}

{description}

{#if cure_period_days}
Cure Period: The Borrower shall have {cure_period_days} Business Days from the date of
notice to remedy this Event of Default.
{/if}

{#if is_cross_default}
[CROSS-DEFAULT PROVISION]
This Event of Default includes a cross-default provision. If the Borrower defaults on any
other financial obligation exceeding {base_currency} {cross_default_threshold | currency:'':2},
such default shall constitute an Event of Default under this Letter.
{/if}

{/events_of_default}

17.{events_of_default | count + 1} CONSEQUENCES OF DEFAULT

Upon the occurrence of an Event of Default which is continuing:
(a) the Lender may by notice to the Borrower cancel the Facilities;
(b) the Lender may declare all outstanding amounts immediately due and payable;
(c) interest shall accrue at the Default Interest Rate ({default_interest_margin | percent:2}
    above the applicable rate);
(d) the Lender may enforce any security granted under this Letter.

Events of Default Summary:
  Total Events: {events_of_default | count}
  With Cure Period: {events_of_default | where:'cure_period_days > 0' | count}
  Cross-Default: {events_of_default | where:'is_cross_default === true' | count}
```

---

## Segment 13: Part K — Governing Law & Jurisdiction (~3 pages)

```
PART K — GOVERNING LAW AND JURISDICTION

18. GOVERNING LAW

This Letter and any non-contractual obligations arising out of or in connection with it
shall be governed by and construed in accordance with the laws of
{governing_law.law_jurisdiction}.

19. JURISDICTION

{#if governing_law.is_arbitration}
19.1 ARBITRATION

Any dispute arising out of or in connection with this Letter, including any question
regarding its existence, validity, or termination, shall be referred to and finally resolved
by arbitration under the rules of {governing_law.arbitration_rules | default:'ICC Rules'}
administered by {governing_law.arbitration_institution | default:'ICC'}.

The seat of arbitration shall be {governing_law.arbitration_seat | default:governing_law.court_jurisdiction}.
The language of the arbitration shall be {governing_law.language}.
The arbitral tribunal shall consist of three (3) arbitrators.

{/if}

{#if !governing_law.is_arbitration}
19.1 COURT JURISDICTION

The courts of {governing_law.court_jurisdiction} shall have exclusive jurisdiction to settle
any dispute arising out of or in connection with this Letter.

{/if}

20. LANGUAGE

This Letter is drawn up in {governing_law.language}. In the event of any conflict between
the {governing_law.language} version and any translation, the {governing_law.language}
version shall prevail.

21. SERVICE OF PROCESS

The Borrower irrevocably appoints its authorized representative at:
{borrower.registered_address.line1}
{borrower.registered_address.city}, {borrower.registered_address.country}
as its agent for service of process in any proceedings before the courts of
{governing_law.court_jurisdiction}.
```

---

## Segment 14: Part L — Miscellaneous (~3 pages)

```
PART L — MISCELLANEOUS

22. NOTICES

22.1 Any notice or communication under this Letter shall be in writing and shall be
delivered by hand, sent by registered mail, or transmitted by email to:

If to the Lender:
  {bank.legal_name}
  Attn: {bank.contact.name}, {bank.contact.title}
  {#if bank.contact.department}Department: {bank.contact.department}{/if}
  {bank.address.line1}
  {bank.address.city}, {bank.address.postal_code}, {bank.address.country}
  Email: {bank.contact.email}
  Phone: {bank.contact.phone}

If to the Borrower:
  {borrower.legal_name}
  Attn: {borrower.authorized_signatories | first}
  {borrower.registered_address.line1}
  {borrower.registered_address.city}, {borrower.registered_address.postal_code}
  {borrower.registered_address.country}

23. AMENDMENTS AND WAIVERS

No amendment or waiver of any provision of this Letter shall be effective unless in writing
and signed by both parties.

24. ASSIGNMENT

The Borrower may not assign or transfer any of its rights or obligations under this Letter
without the prior written consent of the Lender.

{#if is_syndicated}
The Lender may assign or transfer all or part of its rights and obligations to any other
Lender or financial institution, subject to the terms of the syndication agreement.
{/if}

25. SEVERABILITY

If any provision of this Letter is or becomes illegal, invalid, or unenforceable, that shall
not affect the validity or enforceability of any other provision.

26. COUNTERPARTS

This Letter may be executed in any number of counterparts, each of which shall be deemed
an original.

27. ENTIRE AGREEMENT

This Letter, together with its Appendices, constitutes the entire agreement between the
parties in relation to the Facilities and supersedes all previous agreements.

28. ACCEPTANCE

This offer is open for acceptance until {acceptance_deadline | dateFormat:'DD MMMM YYYY'}
({acceptance_period_days} calendar days from the date hereof). If not accepted by such date,
this offer shall automatically lapse.

To accept this offer, the Borrower should sign and return the duplicate copy of this Letter
to the Lender at the address specified in Clause 22.

{#if appendix_notes}
29. ADDITIONAL NOTES

{#appendix_notes | sortBy:'note_number'}
  {note_number}. {content}
{/appendix_notes}
{/if}
```

---

## Segment 15: Appendix A — Compliance Certificate (~2 pages)

```
APPENDIX A — FORM OF COMPLIANCE CERTIFICATE

To: {bank.legal_name}
From: {borrower.legal_name}
Date: [Date]
Reference: {document_ref}

Dear Sirs,

COMPLIANCE CERTIFICATE

1. We refer to the Facility Offer Letter dated {document_date | dateFormat:'DD MMMM YYYY'}
   (the "Letter"). Terms defined in the Letter have the same meaning in this Certificate.

2. We confirm that as at [Testing Date]:

{#covenants | where:'type === "FINANCIAL"'}
   {covenant_name}:
   Required: {threshold_value | toFixed:2} {threshold_unit | default:''}
   Actual: [_____________]
   Compliant: [Yes / No]

{/covenants}

3. {#if !covenants | where:'type === "FINANCIAL"'}No financial covenants to report.{/if}

4. No Event of Default is continuing.

Signed for and on behalf of
{borrower.legal_name | upper}

Name: ____________________________
Title: ____________________________
Date: ____________________________
```

---

## Segment 16: Appendix B — Drawdown Notice (~2 pages)

```
APPENDIX B — FORM OF DRAWDOWN NOTICE

To: {bank.legal_name}
    {bank.address.line1}, {bank.address.city}, {bank.address.country}
    SWIFT: {bank.swift_code}

From: {borrower.legal_name}
Date: [Date]
Reference: {document_ref}

Dear Sirs,

DRAWDOWN NOTICE

1. We refer to the Facility Offer Letter dated {document_date | dateFormat:'DD MMMM YYYY'}
   (the "Letter"). Terms defined in the Letter have the same meaning in this Notice.

2. We wish to draw down under the following Facility:

{#facilities}
   □ {facility_name} — {currency} {amount | currency:'':2}
     Available from: {drawdown_date | dateFormat:'DD MMM YYYY' | default:'[Date]'}
     Maturity: {maturity_date | dateFormat:'DD MMM YYYY'}
{/facilities}

3. Drawdown Details:
   Facility: [Select from above]
   Amount: [_____________]
   Currency: {base_currency}
   Value Date: [_____________]
   Interest Period: [1 / 3 / 6] months
   Purpose: [_____________]

4. Payment Instructions:
   Bank: [_____________]
   Account Name: {borrower.legal_name}
   Account Number: [_____________]
   SWIFT: [_____________]

5. We confirm that:
   (a) the representations in Part F are true and correct;
   (b) no Event of Default is continuing;
   (c) all Conditions Precedent have been satisfied.

Signed for and on behalf of
{borrower.legal_name | upper}

Name: ____________________________
Title: ____________________________
Date: ____________________________
```

---

## Segment 17: Appendix C — Fee Schedule (~2 pages)

```
APPENDIX C — FEE SCHEDULE

Reference: {document_ref}
Date: {document_date | dateFormat:'DD MMMM YYYY'}
Borrower: {borrower.legal_name}

COMPLETE FEE SCHEDULE

Total Fees: {base_currency} {total_fee_amount | currency:'':2}

┌────┬────────────────────────────┬──────────┬──────────────┬──────────────┬──────────────┬───────────┐
│ #  │ Fee Name                   │ Category │ Amount       │ Basis        │ Due Date     │ Refund    │
├────┼────────────────────────────┼──────────┼──────────────┼──────────────┼──────────────┼───────────┤
{#fees | sortBy:'category':'fee_name'}
│ {$index + 1 | padStart:2} │ {fee_name | padEnd:28} │ {category | padEnd:8} │ {currency} {amount | currency:'':2 | padStart:8} │ {calculation_basis | default:'Flat' | padEnd:12} │ {due_date | dateFormat:'DD MMM YY' | default:'On signing' | padEnd:12} │ {#if is_refundable}Yes{/if}{#if !is_refundable}No{/if}       │
{/fees}
├────┼────────────────────────────┼──────────┼──────────────┼──────────────┼──────────────┼───────────┤
│    │ GRAND TOTAL                │          │ {fees.$sum_amount | currency:'':2}        │              │              │           │
└────┴────────────────────────────┴──────────┴──────────────┴──────────────┴──────────────┴───────────┘

BREAKDOWN BY CATEGORY:

{#fees | groupBy:'category'}
{key | upper | replace:'_':' '}:
  Items: {items | count}
  Total: {items | sumBy:'amount' | currency:'':2}
  Average: {items | avgBy:'amount' | currency:'':2}
  Min: {items | minBy:'amount' | currency:'':2}
  Max: {items | maxBy:'amount' | currency:'':2}
  Fee Names: {items | joinBy:'fee_name':'; '}

{/fees}

PAYMENT INSTRUCTIONS:

All fees shall be paid to:
Bank: {bank.legal_name}
SWIFT: {bank.swift_code}
{#if agent_bank}
Agent Bank: {agent_bank.name}
Account: {agent_bank.account_number}
SWIFT: {agent_bank.swift_code}
{/if}
```

---

## Segment 18: Signature Page (~2 pages)

```
SIGNATURE PAGE

Reference: {document_ref}
Date: {document_date | dateFormat:'DD MMMM YYYY'}

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

FOR AND ON BEHALF OF THE LENDER

{bank.legal_name | upper}

{#bank_signatories}
Name: {name}
Title: {title}
{#if signature_image}{%signature_image}{/if}

Signature: ____________________________

Date: ____________________________

{/bank_signatories}

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

ACCEPTED AND AGREED BY THE BORROWER

{borrower.legal_name | upper}
Registration No.: {borrower.registration_number}
Incorporated in: {borrower.incorporation_country}

{#borrower.authorized_signatories}
Name: {name}
Title: {title}
Email: {email}
{#if specimen_signature}{%specimen_signature}{/if}

Signature: ____________________________

Date: ____________________________

{/borrower.authorized_signatories}

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

{#if has_guarantor}
ACCEPTED AND AGREED BY THE GUARANTOR(S)

{#guarantors}
{name | upper}
Type: {type | replace:'_':' '}
Guarantee Amount: {guarantee_currency} {guarantee_amount | currency:'':2}

Signature: ____________________________
Name: ____________________________
Title: ____________________________
Date: ____________________________

{/guarantors}
{/if}

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Document Tracking Barcode:
[Barcode: {document_ref}]

Electronic Verification QR Code:
[QR Code: https://verify.bank.com/fol/{document_ref}]

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
END OF FACILITY OFFER LETTER
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
```

---

## Watermark Configuration (应用于渲染请求)

```json
{
  "watermark": {
    "type": "text",
    "text": "CONFIDENTIAL",
    "fontSize": 54,
    "color": "#D0D0D0",
    "opacity": 0.3,
    "rotation": -45
  }
}
```

## Barcode/QR Code Configuration (应用于渲染请求)

```json
{
  "barcodes": {
    "tracking_barcode": {
      "type": "barcode",
      "value": "FOL-2026-000001",
      "format": "code128",
      "options": { "scale": 3, "height": 12, "includeText": true }
    },
    "verification_qrcode": {
      "type": "qrcode",
      "value": "https://verify.bank.com/fol/FOL-2026-000001",
      "options": { "width": 150, "errorCorrectionLevel": "H" }
    }
  }
}
```
