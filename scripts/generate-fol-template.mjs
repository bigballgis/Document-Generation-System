/**
 * Generate complete FOL (Facility Offer Letter) composite template ZIP
 * with professional formatting and docxtemplater tags.
 * 
 * Usage: node scripts/generate-fol-template.mjs
 * Output: scripts/output/fol-template-import.zip
 */
import {
  Document, Packer, Paragraph, TextRun, Table, TableRow, TableCell,
  HeadingLevel, AlignmentType, BorderStyle, WidthType, ShadingType,
  PageBreak, TabStopPosition, TabStopType, Header, Footer,
  PageNumber, NumberFormat, convertInchesToTwip
} from 'docx';
import * as fs from 'fs';
import * as path from 'path';
import archiver from 'archiver';

// ── Shared styles ──
const FONT = 'Times New Roman';
const FONT_SIZE = 22; // 11pt in half-points
const HEADING_COLOR = '1B3A5C';
const TAG_COLOR = '8B0000';

function tag(t) {
  return new TextRun({ text: t, font: FONT, size: FONT_SIZE, color: TAG_COLOR });
}
function txt(t, opts = {}) {
  return new TextRun({ text: t, font: FONT, size: FONT_SIZE, ...opts });
}
function bold(t, opts = {}) {
  return new TextRun({ text: t, font: FONT, size: FONT_SIZE, bold: true, ...opts });
}
function heading(text, level = HeadingLevel.HEADING_1) {
  return new Paragraph({
    heading: level,
    spacing: { before: 240, after: 120 },
    children: [new TextRun({ text, font: FONT, bold: true, color: HEADING_COLOR, size: level === HeadingLevel.HEADING_1 ? 32 : level === HeadingLevel.HEADING_2 ? 28 : 24 })],
  });
}
function para(children, opts = {}) {
  return new Paragraph({ spacing: { after: 120 }, children: Array.isArray(children) ? children : [children], ...opts });
}
function emptyLine() {
  return new Paragraph({ spacing: { after: 120 }, children: [] });
}
function bulletPara(children) {
  return new Paragraph({ spacing: { after: 80 }, bullet: { level: 0 }, children: Array.isArray(children) ? children : [children] });
}

// Simple table helper
function simpleTable(rows) {
  return new Table({
    width: { size: 100, type: WidthType.PERCENTAGE },
    rows: rows.map((cells, ri) =>
      new TableRow({
        children: cells.map(c => new TableCell({
          width: { size: Math.floor(100 / cells.length), type: WidthType.PERCENTAGE },
          shading: ri === 0 ? { type: ShadingType.SOLID, color: HEADING_COLOR } : undefined,
          children: [new Paragraph({
            spacing: { before: 40, after: 40 },
            children: [typeof c === 'string'
              ? new TextRun({ text: c, font: FONT, size: 20, bold: ri === 0, color: ri === 0 ? 'FFFFFF' : '000000' })
              : c]
          })],
        })),
      })
    ),
  });
}


// ══════════════════════════════════════════════════════════════
// Segment 01: Cover Page
// ══════════════════════════════════════════════════════════════
function buildCoverPage() {
  return new Document({
    sections: [{
      properties: { page: { margin: { top: convertInchesToTwip(1.5), bottom: convertInchesToTwip(1), left: convertInchesToTwip(1.2), right: convertInchesToTwip(1.2) } } },
      children: [
        emptyLine(), emptyLine(), emptyLine(),
        para([tag('{%bank_logo}')], { alignment: AlignmentType.CENTER }),
        emptyLine(),
        new Paragraph({ alignment: AlignmentType.CENTER, spacing: { after: 200 }, children: [new TextRun({ text: '━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━', font: FONT, size: 20, color: HEADING_COLOR })] }),
        para([tag('{confidentiality_level | upper}')], { alignment: AlignmentType.CENTER }),
        new Paragraph({ alignment: AlignmentType.CENTER, spacing: { after: 200 }, children: [new TextRun({ text: '━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━', font: FONT, size: 20, color: HEADING_COLOR })] }),
        emptyLine(),
        para([new TextRun({ text: 'FACILITY OFFER LETTER', font: FONT, size: 48, bold: true, color: HEADING_COLOR })], { alignment: AlignmentType.CENTER }),
        emptyLine(), emptyLine(),
        para([bold('Reference: '), tag('{document_ref}')]),
        para([bold('Date: '), tag('{document_date | dateFormat:\'DD MMMM YYYY\'}')]),
        para([bold('Version: '), tag('{document_version | default:\'1.0\'}')]),
        emptyLine(),
        heading('FROM:', HeadingLevel.HEADING_2),
        para([tag('{bank.legal_name | upper}')]),
        para([tag('{bank.address.line1}')]),
        para([tag('{#if bank.address.line2}{bank.address.line2}{/if}')]),
        para([tag('{bank.address.city}'), txt(', '), tag('{bank.address.state | default:\'\'}'), txt(' '), tag('{bank.address.postal_code}')]),
        para([tag('{bank.address.country}')]),
        para([txt('SWIFT: '), tag('{bank.swift_code}')]),
        para([tag('{#if bank.lei_code}'), txt('LEI: '), tag('{bank.lei_code}'), tag('{/if}')]),
        para([txt('License No.: '), tag('{bank.license_number}')]),
        emptyLine(),
        heading('TO:', HeadingLevel.HEADING_2),
        para([tag('{borrower.legal_name | upper}')]),
        para([tag('{borrower.registered_address.line1}')]),
        para([tag('{#if borrower.registered_address.line2}{borrower.registered_address.line2}{/if}')]),
        para([tag('{borrower.registered_address.city}'), txt(', '), tag('{borrower.registered_address.state | default:\'\'}'), txt(' '), tag('{borrower.registered_address.postal_code}')]),
        para([tag('{borrower.registered_address.country}')]),
        para([txt('Registration No.: '), tag('{borrower.registration_number}')]),
        para([txt('Tax ID: '), tag('{borrower.tax_id}')]),
        emptyLine(), emptyLine(),
        para([bold('Total Facility Amount: '), tag('{base_currency}'), txt(' '), tag('{total_facility_amount | currency:\'\':2}')], { alignment: AlignmentType.CENTER }),
        emptyLine(),
        para([bold('Acceptance Deadline: '), tag('{acceptance_deadline | dateFormat:\'DD MMMM YYYY\'}')], { alignment: AlignmentType.CENTER }),
      ],
    }],
  });
}

// ══════════════════════════════════════════════════════════════
// Segment 02: Table of Contents
// ══════════════════════════════════════════════════════════════
function buildTableOfContents() {
  const tocEntries = [
    'Part A    Definitions and Interpretation',
    'Part B    Facility Details',
    'Part C    Interest and Fees',
    'Part D    Repayment Schedule',
    'Part E    Conditions Precedent',
    'Part F    Representations and Warranties',
    'Part G    Covenants',
    '{#if has_security}Part H    Security and Collateral{/if}',
    '{#if has_guarantor}Part I    Guarantee{/if}',
    'Part J    Events of Default',
    'Part K    Governing Law and Jurisdiction',
    'Part L    Miscellaneous',
    'Appendix A    Form of Compliance Certificate',
    'Appendix B    Form of Drawdown Notice',
    'Appendix C    Fee Schedule',
    'Signature Page',
  ];
  return new Document({
    sections: [{
      children: [
        heading('TABLE OF CONTENTS'),
        emptyLine(),
        ...tocEntries.map(e => {
          const isTag = e.startsWith('{');
          return para([isTag ? tag(e) : txt(e)], { spacing: { after: 80 } });
        }),
        emptyLine(),
        para([txt('Number of Facilities: '), tag('{facilities | count}')]),
        para([tag('{#if is_syndicated}'), txt('Syndicate Members: '), tag('{syndicate_members | count}'), tag('{/if}')]),
      ],
    }],
  });
}


// ══════════════════════════════════════════════════════════════
// Segment 03: Part A — Definitions & Interpretation
// ══════════════════════════════════════════════════════════════
function buildDefinitions() {
  return new Document({
    sections: [{
      children: [
        heading('PART A — DEFINITIONS AND INTERPRETATION'),
        heading('1. DEFINITIONS', HeadingLevel.HEADING_2),
        para([txt('In this Facility Offer Letter, unless the context otherwise requires, the following terms shall have the meanings set out below:')]),
        emptyLine(),
        para([bold('"Acceptance Deadline"'), txt(' means '), tag('{acceptance_deadline | dateFormat:\'DD MMMM YYYY\'}'), txt(', being '), tag('{acceptance_period_days}'), txt(' calendar days from the date of this Letter.')]),
        emptyLine(),
        para([bold('"Agent Bank"'), txt(' means '), tag('{#if agent_bank}'), tag('{agent_bank.name}'), tag('{/if}'), tag('{#if !agent_bank}'), tag('{bank.legal_name}'), tag('{/if}'), txt(', acting in its capacity as agent for the Lenders.')]),
        emptyLine(),
        para([bold('"Base Currency"'), txt(' means '), tag('{base_currency}'), txt('.')]),
        emptyLine(),
        para([bold('"Borrower"'), txt(' means '), tag('{borrower.legal_name}'), txt(', a company incorporated in '), tag('{borrower.incorporation_country}'), txt(' on '), tag('{borrower.incorporation_date | dateFormat:\'DD MMMM YYYY\'}'), txt(' with registration number '), tag('{borrower.registration_number}'), txt('.')]),
        emptyLine(),
        para([tag('{#if borrower.credit_rating}')]),
        para([bold('"Credit Rating"'), txt(' means the rating of '), tag('{borrower.credit_rating.rating}'), txt(' assigned by '), tag('{borrower.credit_rating.agency}'), txt(' on '), tag('{borrower.credit_rating.rating_date | dateFormat:\'DD MMMM YYYY\'}'), txt(' with outlook '), tag('{borrower.credit_rating.outlook | default:\'Stable\'}'), txt('.')]),
        para([tag('{/if}')]),
        emptyLine(),
        para([bold('"Default Interest Rate"'), txt(' means the applicable Interest Rate plus '), tag('{default_interest_margin | percent:2}'), txt(' per annum.')]),
        emptyLine(),
        para([bold('"Effective Date"'), txt(' means '), tag('{effective_date | dateFormat:\'DD MMMM YYYY\'}'), txt('.')]),
        emptyLine(),
        para([bold('"Expiry Date"'), txt(' means '), tag('{expiry_date | dateFormat:\'DD MMMM YYYY\'}'), txt('.')]),
        emptyLine(),
        para([bold('"Facility"'), txt(' or '), bold('"Facilities"'), txt(' means the credit facility(ies) described in Part B, being:')]),
        para([tag('{#facilities}')]),
        bulletPara([txt('('), tag('{facility_name}'), txt('): '), tag('{currency}'), txt(' '), tag('{amount | currency:\'\':2}'), txt(' — '), tag('{facility_type | replace:\'_\':\' \'}')]),
        para([tag('{/facilities}')]),
        emptyLine(),
        para([bold('"Facility Amount"'), txt(' means the aggregate amount of '), tag('{base_currency}'), txt(' '), tag('{total_facility_amount | currency:\'\':2}'), txt(', comprising '), tag('{facilities | count}'), txt(' separate facility(ies).')]),
        emptyLine(),
        para([bold('"Fee Schedule"'), txt(' means the schedule of fees set out in Appendix C, totaling '), tag('{base_currency}'), txt(' '), tag('{total_fee_amount | currency:\'\':2}'), txt('.')]),
        emptyLine(),
        para([tag('{#if has_guarantor}')]),
        para([bold('"Guarantor(s)"'), txt(' means:')]),
        para([tag('{#guarantors}')]),
        bulletPara([tag('{name}'), txt(' ('), tag('{type | lower}'), txt(' guarantor), providing a '), tag('{guarantee_type | replace:\'_\':\' \' | lower}'), txt(' guarantee of '), tag('{guarantee_currency}'), txt(' '), tag('{guarantee_amount | currency:\'\':2}')]),
        para([tag('{/guarantors}')]),
        para([tag('{/if}')]),
        emptyLine(),
        para([bold('"Interest Period"'), txt(' means each period of one (1), three (3), or six (6) months as selected by the Borrower, or such other period as agreed between the parties.')]),
        emptyLine(),
        para([bold('"Material Adverse Change"'), txt(' means any event or circumstance which, in the reasonable opinion of the Lender, has or could have a material adverse effect on:')]),
        para([txt('(a) the business, operations, property, condition (financial or otherwise) of the Borrower;')]),
        para([txt('(b) the ability of the Borrower to perform its obligations under this Agreement;')]),
        para([txt('(c) the validity or enforceability of this Agreement.')]),
        emptyLine(),
        para([bold('"Prepayment Notice Period"'), txt(' means '), tag('{prepayment_notice_days}'), txt(' Business Days.')]),
        emptyLine(),
        para([tag('{#if has_security}')]),
        para([bold('"Security"'), txt(' means the security interests described in Part H, with an aggregate estimated value of '), tag('{base_currency}'), txt(' '), tag('{total_security_value | currency:\'\':2}'), txt(', resulting in an overall loan-to-value ratio of '), tag('{overall_ltv | percent:1}'), txt('.')]),
        para([tag('{/if}')]),
        emptyLine(),
        heading('2. INTERPRETATION', HeadingLevel.HEADING_2),
        para([txt('2.1 In this Letter:')]),
        para([txt('    (a) references to "this Letter" include its Appendices;')]),
        para([txt('    (b) headings are for convenience only and shall not affect interpretation;')]),
        para([txt('    (c) words importing the singular include the plural and vice versa;')]),
        para([txt('    (d) references to a "person" include any individual, company, partnership, or other entity;')]),
        para([txt('    (e) references to legislation include any amendment, re-enactment, or replacement thereof;')]),
        para([txt('    (f) all monetary amounts are expressed in '), tag('{base_currency}'), txt(' unless otherwise stated;')]),
        para([txt('    (g) time is of the essence in respect of all obligations under this Letter.')]),
        emptyLine(),
        para([txt('2.2 The Borrower\'s Industry Sector: '), tag('{borrower.industry_sector}')]),
        para([txt('    '), tag('{#if borrower.sic_code}'), txt('SIC Code: '), tag('{borrower.sic_code}'), tag('{/if}')]),
        emptyLine(),
        para([txt('2.3 Authorized Signatories of the Borrower:')]),
        para([tag('{#borrower.authorized_signatories}')]),
        para([txt('    Name: '), tag('{name}')]),
        para([txt('    Title: '), tag('{title}')]),
        para([txt('    Email: '), tag('{email}')]),
        para([txt('    '), tag('{#if phone}'), txt('Phone: '), tag('{phone}'), tag('{/if}')]),
        para([tag('{/borrower.authorized_signatories}')]),
        emptyLine(),
        para([txt('2.4 Directors of the Borrower:')]),
        para([tag('{#borrower.directors | sortBy:\'appointment_date\'}')]),
        para([txt('    '), tag('{name}'), txt(' ('), tag('{nationality}'), txt(') — Appointed: '), tag('{appointment_date | dateFormat:\'DD MMM YYYY\'}')]),
        para([tag('{/borrower.directors}')]),
      ],
    }],
  });
}


// ══════════════════════════════════════════════════════════════
// Segment 04: Part B — Facility Details
// ══════════════════════════════════════════════════════════════
function buildFacilityDetails() {
  return new Document({
    sections: [{
      children: [
        heading('PART B — FACILITY DETAILS'),
        heading('3. THE FACILITIES', HeadingLevel.HEADING_2),
        para([txt('3.1 Subject to the terms and conditions of this Letter, the Lender hereby offers to make available to the Borrower the following credit facilities:')]),
        emptyLine(),
        heading('Summary of Facilities', HeadingLevel.HEADING_3),
        simpleTable([
          ['Item', 'Value'],
          ['Total Number of Facilities', tag('{facilities.$count}')],
          ['Total Facility Amount', new TextRun({ text: '', children: [tag('{base_currency}'), txt(' '), tag('{total_facility_amount | currency:\'\':2}')] })],
          ['Weighted Average Rate', tag('{weighted_avg_rate | percent:4}')],
          ['Maximum Tenor', new TextRun({ text: '', children: [tag('{max_tenor_months}'), txt(' months')] })],
        ]),
        emptyLine(),
        para([tag('{#facilities}')]),
        heading('{facility_name | upper}', HeadingLevel.HEADING_2),
        simpleTable([
          ['Parameter', 'Details'],
          ['Facility Type', tag('{facility_type | replace:\'_\':\' \'}')],
          ['Currency', tag('{currency}')],
          ['Amount', new TextRun({ text: '', children: [tag('{currency}'), txt(' '), tag('{amount | currency:\'\':2}')] })],
          ['Interest Rate', new TextRun({ text: '', children: [tag('{interest_rate | percent:2}'), txt(' per annum ('), tag('{rate_type}'), txt(')')] })],
          ['Benchmark Rate', tag('{benchmark_rate | default:\'N/A\'}')],
          ['Spread', new TextRun({ text: '', children: [tag('{spread | default:0}'), txt(' bps')] })],
          ['Tenor', new TextRun({ text: '', children: [tag('{tenor_months}'), txt(' months')] })],
          ['Maturity Date', tag('{maturity_date | dateFormat:\'DD MMM YYYY\'}')],
          ['Purpose', tag('{purpose}')],
          ['Committed', new TextRun({ text: '', children: [tag('{#if is_committed}'), txt('Yes'), tag('{/if}'), tag('{#if !is_committed}'), txt('No'), tag('{/if}')] })],
        ]),
        emptyLine(),
        para([tag('{#if sub_limits}')]),
        para([bold('Sub-Limits under '), tag('{facility_name}'), txt(':')]),
        para([tag('{#sub_limits}')]),
        bulletPara([tag('{sub_limit_name}'), txt(': '), tag('{sub_limit_currency}'), txt(' '), tag('{sub_limit_amount | currency:\'\':2}')]),
        para([tag('{/sub_limits}')]),
        para([tag('{/if}')]),
        para([tag('{/facilities}')]),
        emptyLine(),
        para([tag('{#if is_syndicated}')]),
        heading('3.2 SYNDICATE STRUCTURE', HeadingLevel.HEADING_2),
        para([txt('This Facility is arranged on a syndicated basis. The syndicate comprises the following Lenders:')]),
        emptyLine(),
        para([tag('{#syndicate_members | sortBy:\'commitment_percentage\' | reverse}')]),
        bulletPara([tag('{bank_name}'), txt(' ('), tag('{role | replace:\'_\':\' \'}'), txt(') — '), tag('{currency}'), txt(' '), tag('{commitment_amount | currency:\'\':2}'), txt(' ('), tag('{commitment_percentage | percent:1}'), txt(')')]),
        para([tag('{/syndicate_members}')]),
        emptyLine(),
        para([bold('Agent Bank: '), tag('{agent_bank.name | default:\'N/A\'}')]),
        para([tag('{/if}')]),
        emptyLine(),
        para([tag('{#if is_revolving}')]),
        heading('3.3 REVOLVING FACILITY PROVISIONS', HeadingLevel.HEADING_2),
        para([txt('The Borrower may draw down, repay, and re-draw amounts under the Revolving Credit Facility subject to the following conditions:')]),
        para([txt('(a) Each drawdown shall be in a minimum amount of '), tag('{base_currency}'), txt(' 100,000;')]),
        para([txt('(b) The aggregate outstanding amount shall not exceed the Facility Amount;')]),
        para([txt('(c) The Borrower shall provide at least 3 Business Days\' notice for each drawdown;')]),
        para([txt('(d) Interest shall accrue on drawn amounts at the applicable rate.')]),
        para([tag('{/if}')]),
      ],
    }],
  });
}

// ══════════════════════════════════════════════════════════════
// Segment 05: Part C — Interest & Fees
// ══════════════════════════════════════════════════════════════
function buildInterestFees() {
  return new Document({
    sections: [{
      children: [
        heading('PART C — INTEREST AND FEES'),
        heading('4. INTEREST', HeadingLevel.HEADING_2),
        para([txt('4.1 The rate of interest applicable to each Facility shall be as follows:')]),
        emptyLine(),
        para([tag('{#facilities}')]),
        para([bold('{facility_name}'), txt(':')]),
        para([txt('  Base Rate: '), tag('{interest_rate | percent:2}'), txt(' per annum')]),
        para([txt('  Rate Type: '), tag('{rate_type}')]),
        para([txt('  '), tag('{#if rate_type === \'FLOATING\'}'), txt('Benchmark: '), tag('{benchmark_rate}'), txt(' + '), tag('{spread}'), txt(' bps'), tag('{/if}')]),
        para([txt('  '), tag('{#if rate_type === \'FIXED\'}'), txt('Fixed Rate: '), tag('{interest_rate | percent:4}'), txt(' per annum'), tag('{/if}')]),
        para([tag('{/facilities}')]),
        emptyLine(),
        para([txt('4.2 Interest shall accrue on each Facility during successive Interest Periods. Each Interest Period shall be one (1), three (3), or six (6) months, as selected by the Borrower.')]),
        emptyLine(),
        para([txt('4.3 If the Borrower fails to pay any amount payable by it under this Letter on its due date, interest shall accrue on the overdue amount at a rate which is '), tag('{default_interest_margin | percent:2}'), txt(' per annum above the applicable Interest Rate.')]),
        emptyLine(),
        para([txt('4.4 Interest shall be calculated on the basis of the actual number of days elapsed and a year of 360 days (or 365 days for GBP).')]),
        emptyLine(),
        heading('5. FEES', HeadingLevel.HEADING_2),
        para([txt('5.1 The Borrower shall pay to the Lender the following fees:')]),
        para([bold('Total Fees: '), tag('{base_currency}'), txt(' '), tag('{total_fee_amount | currency:\'\':2}')]),
        para([bold('Number of Fee Items: '), tag('{fees | count}')]),
        emptyLine(),
        para([tag('{#fees | groupBy:\'category\'}')]),
        para([bold('{key | upper | replace:\'_\':\' \'}'), txt(' FEES:')]),
        para([tag('{#items | sortBy:\'amount\' | reverse}')]),
        bulletPara([tag('{fee_name}'), txt(': '), tag('{currency}'), txt(' '), tag('{amount | currency:\'\':2}')]),
        para([txt('    Calculation: '), tag('{calculation_basis | default:\'Flat fee\'}')]),
        para([txt('    '), tag('{#if percentage_rate}'), txt('Rate: '), tag('{percentage_rate | percent:2}'), tag('{/if}')]),
        para([txt('    Refundable: '), tag('{#if is_refundable}'), txt('Yes'), tag('{/if}'), tag('{#if !is_refundable}'), txt('No'), tag('{/if}')]),
        para([tag('{/items}')]),
        para([tag('{/fees}')]),
        emptyLine(),
        para([txt('5.3 The Borrower may prepay all or part of any Facility by giving not less than '), tag('{prepayment_notice_days}'), txt(' Business Days\' prior written notice to the Lender.')]),
        para([tag('{#if prepayment_penalty_rate}')]),
        para([txt('A prepayment fee of '), tag('{prepayment_penalty_rate | percent:2}'), txt(' of the prepaid amount shall be payable on any voluntary prepayment.')]),
        para([tag('{/if}')]),
      ],
    }],
  });
}


// ══════════════════════════════════════════════════════════════
// Segment 06: Part D — Repayment Schedule
// ══════════════════════════════════════════════════════════════
function buildRepaymentSchedule() {
  return new Document({
    sections: [{
      children: [
        heading('PART D — REPAYMENT SCHEDULE'),
        heading('6. REPAYMENT', HeadingLevel.HEADING_2),
        para([txt('6.1 The Borrower shall repay each Facility in accordance with the following schedule:')]),
        emptyLine(),
        para([tag('{#facilities}')]),
        heading('{facility_name | upper}', HeadingLevel.HEADING_3),
        para([bold('Facility Amount: '), tag('{currency}'), txt(' '), tag('{amount | currency:\'\':2}')]),
        para([bold('Interest Rate: '), tag('{interest_rate | percent:2}'), txt(' ('), tag('{rate_type}'), txt(')')]),
        para([bold('Tenor: '), tag('{tenor_months}'), txt(' months')]),
        para([bold('Maturity: '), tag('{maturity_date | dateFormat:\'DD MMMM YYYY\'}')]),
        emptyLine(),
        para([tag('{#repayment_schedule}')]),
        para([txt('Period '), tag('{period}'), txt(': Due '), tag('{due_date | dateFormat:\'DD MMM YYYY\'}'), txt(' — Principal: '), tag('{principal | currency:\'\':2}'), txt(' | Interest: '), tag('{interest | currency:\'\':2}'), txt(' | Total: '), tag('{total_payment | currency:\'\':2}'), txt(' | Balance: '), tag('{outstanding_balance | currency:\'\':2}')]),
        para([tag('{/repayment_schedule}')]),
        emptyLine(),
        para([bold('Number of Payments: '), tag('{repayment_schedule | count}')]),
        para([tag('{/facilities}')]),
        emptyLine(),
        para([txt('6.2 Each installment shall be paid on the relevant due date. If a due date falls on a day which is not a Business Day, payment shall be made on the next Business Day.')]),
        para([txt('6.3 All payments shall be made in the currency of the relevant Facility to the account specified by the Lender.')]),
      ],
    }],
  });
}

// ══════════════════════════════════════════════════════════════
// Segment 07: Part E — Conditions Precedent
// ══════════════════════════════════════════════════════════════
function buildConditionsPrecedent() {
  return new Document({
    sections: [{
      children: [
        heading('PART E — CONDITIONS PRECEDENT'),
        heading('7. CONDITIONS PRECEDENT TO FIRST DRAWDOWN', HeadingLevel.HEADING_2),
        para([txt('7.1 The obligation of the Lender to make the first Facility available is subject to the Lender having received all of the following documents and evidence, in form and substance satisfactory to the Lender:')]),
        emptyLine(),
        para([bold('Total Conditions: '), tag('{conditions_precedent | count}')]),
        emptyLine(),
        para([tag('{#conditions_precedent | groupBy:\'category\'}')]),
        para([bold('{key | upper}'), txt(' CONDITIONS:')]),
        para([tag('{#items | sortBy:\'cp_number\'}')]),
        para([tag('{cp_number}'), txt('. '), tag('{description}')]),
        para([txt('   Category: '), tag('{category | replace:\'_\':\' \'}')]),
        para([txt('   '), tag('{#if deadline}'), txt('Deadline: '), tag('{deadline | dateFormat:\'DD MMM YYYY\'}'), tag('{/if}')]),
        para([txt('   '), tag('{#if responsible_party}'), txt('Responsible: '), tag('{responsible_party}'), tag('{/if}')]),
        para([txt('   Waivable: '), tag('{#if is_waivable}'), txt('Yes'), tag('{/if}'), tag('{#if !is_waivable}'), txt('No'), tag('{/if}')]),
        para([tag('{/items}')]),
        para([tag('{/conditions_precedent}')]),
        emptyLine(),
        para([txt('7.2 The Lender may, in its sole discretion, waive any Condition Precedent marked as "Waivable" above.')]),
        para([txt('7.3 All Conditions Precedent must be satisfied or waived by '), tag('{effective_date | dateFormat:\'DD MMMM YYYY\'}'), txt('.')]),
      ],
    }],
  });
}

// ══════════════════════════════════════════════════════════════
// Segment 08: Part F — Representations & Warranties
// ══════════════════════════════════════════════════════════════
function buildRepresentations() {
  return new Document({
    sections: [{
      children: [
        heading('PART F — REPRESENTATIONS AND WARRANTIES'),
        heading('8. REPRESENTATIONS AND WARRANTIES', HeadingLevel.HEADING_2),
        para([txt('The Borrower ('), tag('{borrower.legal_name}'), txt(') represents and warrants to the Lender that:')]),
        emptyLine(),
        para([bold('Total Representations: '), tag('{representations | count}')]),
        para([bold('Repeating Representations: '), tag('{representations | where:\'is_repeating === true\' | count}')]),
        emptyLine(),
        para([tag('{#representations | sortBy:\'rep_number\'}')]),
        para([bold('8.'), tag('{rep_number}'), txt(' '), bold('{title | upper}')]),
        para([tag('{description}')]),
        para([tag('{#if qualification}'), txt('Qualification: '), tag('{qualification}'), tag('{/if}')]),
        para([tag('{#if is_repeating}'), txt('[This representation is deemed repeated on each drawdown date and on the first day of each Interest Period.]'), tag('{/if}')]),
        emptyLine(),
        para([tag('{/representations}')]),
        emptyLine(),
        para([txt('The Borrower confirms that:')]),
        para([txt('(a) it is duly incorporated and validly existing under the laws of '), tag('{borrower.incorporation_country}'), txt(';')]),
        para([txt('(b) it has the power to enter into and perform its obligations under this Letter;')]),
        para([txt('(c) this Letter constitutes its legal, valid, and binding obligations;')]),
        para([txt('(d) no Event of Default is continuing or would result from the proposed drawdown.')]),
      ],
    }],
  });
}

// ══════════════════════════════════════════════════════════════
// Segment 09: Part G — Covenants
// ══════════════════════════════════════════════════════════════
function buildCovenants() {
  return new Document({
    sections: [{
      children: [
        heading('PART G — COVENANTS'),
        heading('9. FINANCIAL COVENANTS', HeadingLevel.HEADING_2),
        para([txt('The Borrower undertakes to comply with the following financial covenants, tested at the frequency specified:')]),
        emptyLine(),
        para([tag('{#covenants | where:\'type === "FINANCIAL"\'}')]),
        para([bold('9.'), tag('{covenant_name}')]),
        para([tag('{description}')]),
        para([txt('Threshold: '), tag('{threshold_value | toFixed:2}'), txt(' '), tag('{threshold_unit | default:\'\'}')]),
        para([txt('Testing Frequency: '), tag('{testing_frequency | default:\'Quarterly\'}')]),
        para([txt('Cure Period: '), tag('{cure_period_days | default:0}'), txt(' days')]),
        emptyLine(),
        para([tag('{/covenants}')]),
        emptyLine(),
        heading('10. INFORMATION COVENANTS', HeadingLevel.HEADING_2),
        para([tag('{#covenants | where:\'type === "INFORMATION"\'}')]),
        para([bold('10.'), tag('{covenant_name}'), txt(': '), tag('{description}')]),
        para([txt('Testing: '), tag('{testing_frequency | default:\'As required\'}')]),
        para([tag('{/covenants}')]),
        emptyLine(),
        para([tag('{#if financial_statements}')]),
        heading('10.A FINANCIAL REPORTING REQUIREMENTS', HeadingLevel.HEADING_3),
        para([tag('{#financial_statements}')]),
        bulletPara([tag('{statement_type}'), txt(' — Frequency: '), tag('{frequency}'), txt(', Deadline: '), tag('{deadline_days}'), txt(' days'), tag('{#if requires_audit}'), txt(' [Audited]'), tag('{/if}')]),
        para([tag('{/financial_statements}')]),
        para([tag('{/if}')]),
        emptyLine(),
        heading('11. POSITIVE COVENANTS', HeadingLevel.HEADING_2),
        para([txt('The Borrower undertakes to:')]),
        para([tag('{#covenants | where:\'type === "POSITIVE"\'}')]),
        bulletPara([tag('{covenant_name}'), txt(': '), tag('{description}')]),
        para([tag('{/covenants}')]),
        emptyLine(),
        heading('12. NEGATIVE COVENANTS', HeadingLevel.HEADING_2),
        para([txt('The Borrower undertakes NOT to, without the prior written consent of the Lender:')]),
        para([tag('{#covenants | where:\'type === "NEGATIVE"\'}')]),
        bulletPara([tag('{covenant_name}'), txt(': '), tag('{description}')]),
        para([tag('{/covenants}')]),
        emptyLine(),
        para([bold('Covenant Summary:')]),
        para([txt('  Financial: '), tag('{covenants | where:\'type === "FINANCIAL"\' | count}')]),
        para([txt('  Information: '), tag('{covenants | where:\'type === "INFORMATION"\' | count}')]),
        para([txt('  Positive: '), tag('{covenants | where:\'type === "POSITIVE"\' | count}')]),
        para([txt('  Negative: '), tag('{covenants | where:\'type === "NEGATIVE"\' | count}')]),
        para([txt('  Total: '), tag('{covenants | count}')]),
        emptyLine(),
        para([tag('{#if insurance_requirements}')]),
        heading('14. INSURANCE', HeadingLevel.HEADING_2),
        para([txt('The Borrower shall maintain the following insurance coverage:')]),
        para([tag('{#insurance_requirements}')]),
        bulletPara([tag('{insurance_type}'), txt(': Min Coverage '), tag('{currency}'), txt(' '), tag('{minimum_coverage | currency:\'\':2}'), txt(', Insurer Rating: '), tag('{insurer_rating | default:\'N/A\'}')]),
        para([tag('{/insurance_requirements}')]),
        para([tag('{/if}')]),
      ],
    }],
  });
}


// ══════════════════════════════════════════════════════════════
// Segment 10: Part H — Security & Collateral
// ══════════════════════════════════════════════════════════════
function buildSecurityCollateral() {
  return new Document({
    sections: [{
      children: [
        heading('PART H — SECURITY AND COLLATERAL'),
        heading('15. SECURITY', HeadingLevel.HEADING_2),
        para([txt('15.1 As continuing security for the payment and discharge of all obligations of the Borrower under this Letter, the Borrower shall provide the following security:')]),
        emptyLine(),
        para([bold('Total Security Value: '), tag('{base_currency | default:\'USD\'}'), txt(' '), tag('{total_security_value | currency:\'\':2}')]),
        para([bold('Total Facility Amount: '), tag('{base_currency | default:\'USD\'}'), txt(' '), tag('{total_facility_amount | currency:\'\':2}')]),
        para([bold('Overall LTV Ratio: '), tag('{overall_ltv | percent:1}')]),
        para([bold('Number of Security Items: '), tag('{security_items | count}')]),
        emptyLine(),
        para([tag('{#security_items | sortBy:\'estimated_value\' | reverse}')]),
        heading('{security_type | replace:\'_\':\' \' | upper}', HeadingLevel.HEADING_3),
        para([bold('Description: '), tag('{description}')]),
        para([bold('Estimated Value: '), tag('{currency}'), txt(' '), tag('{estimated_value | currency:\'\':2}')]),
        para([bold('Valuation Date: '), tag('{valuation_date | dateFormat:\'DD MMMM YYYY\'}')]),
        para([tag('{#if valuer_name}'), txt('Valuer: '), tag('{valuer_name}'), tag('{/if}')]),
        para([tag('{#if ltv_ratio}'), txt('LTV Ratio: '), tag('{ltv_ratio | percent:1}'), tag('{/if}')]),
        emptyLine(),
        para([tag('{#if property_details}')]),
        para([bold('Property Details:')]),
        para([txt('  Title Deed: '), tag('{property_details.title_deed_number | default:\'N/A\'}')]),
        para([txt('  Location: '), tag('{property_details.location | default:\'N/A\'}')]),
        para([txt('  Area: '), tag('{property_details.area_sqm | default:0}'), txt(' sqm')]),
        para([txt('  Zoning: '), tag('{property_details.zoning | default:\'N/A\'}')]),
        para([tag('{/if}')]),
        para([tag('{/security_items}')]),
        emptyLine(),
        para([txt('15.2 The Borrower shall ensure that:')]),
        para([txt('(a) all security documents are duly executed and registered;')]),
        para([txt('(b) the security remains valid and enforceable at all times;')]),
        para([txt('(c) the aggregate value of the security is maintained at a level satisfactory to the Lender;')]),
        para([txt('(d) periodic valuations are conducted as required by the Lender.')]),
      ],
    }],
  });
}

// ══════════════════════════════════════════════════════════════
// Segment 11: Part I — Guarantee
// ══════════════════════════════════════════════════════════════
function buildGuarantee() {
  return new Document({
    sections: [{
      children: [
        heading('PART I — GUARANTEE'),
        heading('16. GUARANTEE', HeadingLevel.HEADING_2),
        para([txt('16.1 As further security for the obligations of the Borrower, the following Guarantor(s) shall provide guarantee(s) in favor of the Lender:')]),
        emptyLine(),
        para([bold('Number of Guarantors: '), tag('{guarantors | count}')]),
        para([bold('Total Guarantee Amount: '), tag('{guarantors | sumBy:\'guarantee_amount\' | currency:\'\':2}')]),
        emptyLine(),
        para([tag('{#guarantors}')]),
        heading('{name}', HeadingLevel.HEADING_3),
        para([bold('Type: '), tag('{type | replace:\'_\':\' \'}')]),
        para([bold('Guarantee Type: '), tag('{guarantee_type | replace:\'_\':\' \' | lower}')]),
        para([bold('Amount: '), tag('{guarantee_currency}'), txt(' '), tag('{guarantee_amount | currency:\'\':2}')]),
        emptyLine(),
        para([tag('{/guarantors}')]),
        emptyLine(),
        para([txt('16.2 Each Guarantee shall be unconditional and irrevocable.')]),
        para([txt('16.3 The Guarantor\'s obligations shall not be affected by any amendment to this Letter or any indulgence granted by the Lender to the Borrower.')]),
        para([txt('16.4 The Lender may enforce the Guarantee without first taking proceedings against the Borrower.')]),
      ],
    }],
  });
}

// ══════════════════════════════════════════════════════════════
// Segment 12: Part J — Events of Default
// ══════════════════════════════════════════════════════════════
function buildEventsOfDefault() {
  return new Document({
    sections: [{
      children: [
        heading('PART J — EVENTS OF DEFAULT'),
        heading('17. EVENTS OF DEFAULT', HeadingLevel.HEADING_2),
        para([txt('17.1 Each of the following shall constitute an Event of Default:')]),
        emptyLine(),
        para([tag('{#events_of_default | sortBy:\'eod_number\'}')]),
        para([bold('17.1.'), tag('{eod_number}'), txt(' '), bold('{title | upper}')]),
        para([tag('{description}')]),
        para([tag('{#if cure_period_days}'), txt('Grace Period: '), tag('{cure_period_days}'), txt(' days'), tag('{/if}')]),
        emptyLine(),
        para([tag('{/events_of_default}')]),
        emptyLine(),
        para([txt('17.2 Cross-Default: Any default under any other agreement involving an amount exceeding '), tag('{base_currency}'), txt(' '), tag('{cross_default_threshold | currency:\'\':2}'), txt(' shall constitute an Event of Default.')]),
        emptyLine(),
        para([txt('17.3 Upon the occurrence of an Event of Default, the Lender may:')]),
        para([txt('(a) declare all outstanding amounts immediately due and payable;')]),
        para([txt('(b) cancel any undrawn commitments;')]),
        para([txt('(c) apply default interest at the rate of the applicable rate plus '), tag('{default_interest_margin | percent:2}'), txt(' per annum;')]),
        para([txt('(d) enforce any security held.')]),
      ],
    }],
  });
}

// ══════════════════════════════════════════════════════════════
// Segment 13: Part K — Governing Law & Jurisdiction
// ══════════════════════════════════════════════════════════════
function buildGoverningLaw() {
  return new Document({
    sections: [{
      children: [
        heading('PART K — GOVERNING LAW AND JURISDICTION'),
        heading('18. GOVERNING LAW', HeadingLevel.HEADING_2),
        para([txt('18.1 This Letter and any non-contractual obligations arising out of or in connection with it shall be governed by and construed in accordance with the laws of '), tag('{governing_law.law_jurisdiction}'), txt('.')]),
        emptyLine(),
        heading('19. JURISDICTION', HeadingLevel.HEADING_2),
        para([txt('19.1 The courts of '), tag('{governing_law.court_jurisdiction}'), txt(' shall have exclusive jurisdiction to settle any dispute arising out of or in connection with this Letter.')]),
        emptyLine(),
        para([txt('19.2 The Borrower irrevocably submits to the jurisdiction of such courts and waives any objection to proceedings in such courts on the grounds of venue or on the grounds that proceedings have been brought in an inconvenient forum.')]),
        emptyLine(),
        para([tag('{#if governing_law.arbitration_rules}')]),
        heading('20. ARBITRATION', HeadingLevel.HEADING_2),
        para([txt('20.1 Any dispute arising out of or in connection with this Letter shall be referred to and finally resolved by arbitration under the '), tag('{governing_law.arbitration_rules}'), txt('.')]),
        para([txt('20.2 The seat of arbitration shall be '), tag('{governing_law.arbitration_seat | default:\'London\'}'), txt('.')]),
        para([txt('20.3 The language of the arbitration shall be '), tag('{governing_law.language | default:\'English\'}'), txt('.')]),
        para([tag('{/if}')]),
      ],
    }],
  });
}

// ══════════════════════════════════════════════════════════════
// Segment 14: Part L — Miscellaneous
// ══════════════════════════════════════════════════════════════
function buildMiscellaneous() {
  return new Document({
    sections: [{
      children: [
        heading('PART L — MISCELLANEOUS'),
        heading('21. NOTICES', HeadingLevel.HEADING_2),
        para([txt('21.1 Any notice or other communication under this Letter shall be in writing and shall be delivered personally, sent by prepaid first-class post, or sent by email to the addresses specified in this Letter.')]),
        emptyLine(),
        heading('22. AMENDMENTS', HeadingLevel.HEADING_2),
        para([txt('22.1 No amendment to this Letter shall be effective unless made in writing and signed by both parties.')]),
        emptyLine(),
        heading('23. ASSIGNMENT', HeadingLevel.HEADING_2),
        para([txt('23.1 The Borrower may not assign or transfer any of its rights or obligations under this Letter without the prior written consent of the Lender.')]),
        para([txt('23.2 The Lender may assign or transfer all or part of its rights and obligations under this Letter to any financial institution.')]),
        emptyLine(),
        heading('24. SEVERABILITY', HeadingLevel.HEADING_2),
        para([txt('24.1 If any provision of this Letter is or becomes illegal, invalid, or unenforceable, the legality, validity, and enforceability of the remaining provisions shall not be affected.')]),
        emptyLine(),
        heading('25. ENTIRE AGREEMENT', HeadingLevel.HEADING_2),
        para([txt('25.1 This Letter, together with its Appendices, constitutes the entire agreement between the parties in relation to its subject matter and supersedes all previous agreements, understandings, and arrangements between the parties.')]),
        emptyLine(),
        heading('26. COUNTERPARTS', HeadingLevel.HEADING_2),
        para([txt('26.1 This Letter may be executed in any number of counterparts, each of which shall be deemed an original and all of which together shall constitute one and the same instrument.')]),
      ],
    }],
  });
}


// ══════════════════════════════════════════════════════════════
// Segment 15: Appendix A — Compliance Certificate
// ══════════════════════════════════════════════════════════════
function buildAppendixCompliance() {
  return new Document({
    sections: [{
      children: [
        heading('APPENDIX A — FORM OF COMPLIANCE CERTIFICATE'),
        emptyLine(),
        para([txt('To: '), tag('{bank.legal_name}')]),
        para([txt('From: '), tag('{borrower.legal_name}')]),
        para([txt('Date: [Date]')]),
        emptyLine(),
        para([txt('Dear Sirs,')]),
        emptyLine(),
        para([txt('We refer to the Facility Offer Letter dated '), tag('{document_date | dateFormat:\'DD MMMM YYYY\'}'), txt(' (Reference: '), tag('{document_ref}'), txt(') (the "Letter").')]),
        emptyLine(),
        para([txt('We confirm that as at the date of this certificate:')]),
        emptyLine(),
        para([txt('1. No Event of Default or Potential Event of Default has occurred and is continuing.')]),
        para([txt('2. The representations and warranties set out in Part F of the Letter remain true and correct.')]),
        para([txt('3. We are in compliance with all financial covenants set out in Part G of the Letter, as detailed below:')]),
        emptyLine(),
        para([tag('{#covenants | where:\'type === "FINANCIAL"\'}')]),
        para([bold('{covenant_name}'), txt(': Threshold = '), tag('{threshold_value | toFixed:2}'), txt(' '), tag('{threshold_unit | default:\'\'}'), txt(' — Actual = [_______]')]),
        para([tag('{/covenants}')]),
        emptyLine(),
        para([txt('Signed for and on behalf of')]),
        para([tag('{borrower.legal_name}')]),
        emptyLine(), emptyLine(),
        para([txt('_________________________________')]),
        para([txt('Name:')]),
        para([txt('Title:')]),
        para([txt('Date:')]),
      ],
    }],
  });
}

// ══════════════════════════════════════════════════════════════
// Segment 16: Appendix B — Drawdown Notice
// ══════════════════════════════════════════════════════════════
function buildAppendixDrawdown() {
  return new Document({
    sections: [{
      children: [
        heading('APPENDIX B — FORM OF DRAWDOWN NOTICE'),
        emptyLine(),
        para([txt('To: '), tag('{bank.legal_name}')]),
        para([txt('From: '), tag('{borrower.legal_name}')]),
        para([txt('Date: [Date]')]),
        emptyLine(),
        para([txt('Dear Sirs,')]),
        emptyLine(),
        para([txt('We refer to the Facility Offer Letter dated '), tag('{document_date | dateFormat:\'DD MMMM YYYY\'}'), txt(' (Reference: '), tag('{document_ref}'), txt(') (the "Letter"). Terms defined in the Letter have the same meaning in this notice.')]),
        emptyLine(),
        para([txt('We hereby give you notice that we wish to draw down under the following Facility:')]),
        emptyLine(),
        para([tag('{#facilities}')]),
        para([bold('Facility: '), tag('{facility_name}')]),
        para([bold('Currency: '), tag('{currency}')]),
        para([bold('Available Amount: '), tag('{currency}'), txt(' '), tag('{amount | currency:\'\':2}')]),
        para([tag('{/facilities}')]),
        emptyLine(),
        para([bold('Drawdown Amount: '), txt('[Amount]')]),
        para([bold('Proposed Drawdown Date: '), txt('[Date]')]),
        para([bold('Interest Period: '), txt('[1/3/6] months')]),
        para([bold('Purpose: '), txt('[Purpose of drawdown]')]),
        emptyLine(),
        para([txt('We confirm that:')]),
        para([txt('(a) the representations and warranties in Part F are true and correct;')]),
        para([txt('(b) no Event of Default is continuing or would result from this drawdown;')]),
        para([txt('(c) all Conditions Precedent have been satisfied or waived.')]),
        emptyLine(),
        para([txt('Please credit the proceeds to:')]),
        para([bold('Bank: '), txt('[Bank Name]')]),
        para([bold('Account Name: '), tag('{borrower.legal_name}')]),
        para([bold('Account Number: '), txt('[Account Number]')]),
        para([bold('SWIFT: '), txt('[SWIFT Code]')]),
        emptyLine(), emptyLine(),
        para([txt('_________________________________')]),
        para([txt('Authorized Signatory')]),
        para([tag('{borrower.legal_name}')]),
      ],
    }],
  });
}

// ══════════════════════════════════════════════════════════════
// Segment 17: Appendix C — Fee Schedule
// ══════════════════════════════════════════════════════════════
function buildAppendixFeeSchedule() {
  return new Document({
    sections: [{
      children: [
        heading('APPENDIX C — FEE SCHEDULE'),
        emptyLine(),
        para([txt('The following fees are payable by the Borrower to the Lender in connection with the Facilities:')]),
        emptyLine(),
        para([bold('Total Fees: '), tag('{base_currency}'), txt(' '), tag('{total_fee_amount | currency:\'\':2}')]),
        para([bold('Number of Fee Items: '), tag('{fees | count}')]),
        emptyLine(),
        para([tag('{#fees | sortBy:\'category\':\'amount\'}')]),
        para([bold('{fee_name}')]),
        para([txt('  Category: '), tag('{category | replace:\'_\':\' \'}')]),
        para([txt('  Amount: '), tag('{currency}'), txt(' '), tag('{amount | currency:\'\':2}')]),
        para([txt('  Calculation Basis: '), tag('{calculation_basis | default:\'Flat fee\'}')]),
        para([tag('{#if percentage_rate}'), txt('  Rate: '), tag('{percentage_rate | percent:2}'), tag('{/if}')]),
        para([tag('{#if payment_frequency}'), txt('  Frequency: '), tag('{payment_frequency}'), tag('{/if}')]),
        para([tag('{#if due_date}'), txt('  Due Date: '), tag('{due_date | dateFormat:\'DD MMM YYYY\'}'), tag('{/if}')]),
        para([txt('  Refundable: '), tag('{#if is_refundable}'), txt('Yes'), tag('{/if}'), tag('{#if !is_refundable}'), txt('No'), tag('{/if}')]),
        emptyLine(),
        para([tag('{/fees}')]),
        emptyLine(),
        para([bold('Upfront Fees Total: '), tag('{fees | where:\'category === "UPFRONT"\' | sumBy:\'amount\' | currency:\'\':2}')]),
        para([bold('Recurring Fees Total: '), tag('{fees | where:\'category === "RECURRING"\' | sumBy:\'amount\' | currency:\'\':2}')]),
      ],
    }],
  });
}

// ══════════════════════════════════════════════════════════════
// Segment 18: Signature Page
// ══════════════════════════════════════════════════════════════
function buildSignaturePage() {
  return new Document({
    sections: [{
      children: [
        heading('SIGNATURE PAGE'),
        emptyLine(),
        para([txt('IN WITNESS WHEREOF, the parties hereto have caused this Facility Offer Letter to be duly executed as of the date first written above.')]),
        emptyLine(), emptyLine(),
        heading('THE LENDER', HeadingLevel.HEADING_2),
        emptyLine(),
        para([tag('{bank.legal_name | upper}')]),
        emptyLine(), emptyLine(),
        para([txt('_________________________________')]),
        para([txt('Name:')]),
        para([txt('Title:')]),
        para([txt('Date:')]),
        emptyLine(), emptyLine(), emptyLine(),
        heading('THE BORROWER', HeadingLevel.HEADING_2),
        emptyLine(),
        para([tag('{borrower.legal_name | upper}')]),
        emptyLine(), emptyLine(),
        para([txt('_________________________________')]),
        para([txt('Name:')]),
        para([txt('Title:')]),
        para([txt('Date:')]),
        emptyLine(), emptyLine(),
        para([tag('{#if has_guarantor}')]),
        heading('THE GUARANTOR(S)', HeadingLevel.HEADING_2),
        para([tag('{#guarantors}')]),
        emptyLine(),
        para([tag('{name | upper}')]),
        emptyLine(), emptyLine(),
        para([txt('_________________________________')]),
        para([txt('Name:')]),
        para([txt('Title:')]),
        para([txt('Date:')]),
        para([tag('{/guarantors}')]),
        para([tag('{/if}')]),
      ],
    }],
  });
}


// ══════════════════════════════════════════════════════════════
// Headers & Footers
// ══════════════════════════════════════════════════════════════
function buildCoverHeader() {
  return new Document({
    sections: [{
      children: [
        para([tag('{bank.legal_name}'), txt(' — CONFIDENTIAL')], { alignment: AlignmentType.CENTER }),
      ],
    }],
  });
}
function buildCoverFooter() {
  return new Document({
    sections: [{
      children: [
        para([txt('© '), tag('{bank.legal_name}'), txt(' — All Rights Reserved')], { alignment: AlignmentType.CENTER }),
      ],
    }],
  });
}
function buildStandardHeader() {
  return new Document({
    sections: [{
      children: [
        para([txt('Facility Offer Letter — Ref: '), tag('{document_ref}'), txt(' — '), tag('{confidentiality_level | upper}')], { alignment: AlignmentType.RIGHT }),
      ],
    }],
  });
}
function buildStandardFooter() {
  return new Document({
    sections: [{
      children: [
        para([tag('{bank.legal_name}'), txt(' | '), tag('{borrower.legal_name}')], { alignment: AlignmentType.CENTER }),
      ],
    }],
  });
}
function buildSignatureFooter() {
  return new Document({
    sections: [{
      children: [
        para([txt('Ref: '), tag('{document_ref}'), txt(' — Execution Copy')], { alignment: AlignmentType.CENTER }),
      ],
    }],
  });
}

// ══════════════════════════════════════════════════════════════
// Config JSON for import
// ══════════════════════════════════════════════════════════════
function buildConfig() {
  return {
    templateName: "International Bank FOL — Full Demo",
    templateType: "COMPOSITE",
    description: "Complete Facility Offer Letter template with 18 segments, headers/footers, conditional sections, and comprehensive docxtemplater tags for demo purposes.",
    segments: [
      { segmentName: "Cover Page", segmentType: "BODY", position: 1, enabled: true, pageBreakBefore: false, conditionExpression: null, dataScope: null, headerFileName: "cover-header", footerFileName: "cover-footer", pageNumberFormat: null, pageNumberStart: null },
      { segmentName: "Table of Contents", segmentType: "BODY", position: 2, enabled: true, pageBreakBefore: true, conditionExpression: null, dataScope: null, headerFileName: "standard-header", footerFileName: "standard-footer", pageNumberFormat: "roman_lower", pageNumberStart: 1 },
      { segmentName: "Part A - Definitions & Interpretation", segmentType: "BODY", position: 3, enabled: true, pageBreakBefore: true, conditionExpression: null, dataScope: null, headerFileName: "standard-header", footerFileName: "standard-footer", pageNumberFormat: "decimal", pageNumberStart: 1 },
      { segmentName: "Part B - Facility Details", segmentType: "BODY", position: 4, enabled: true, pageBreakBefore: true, conditionExpression: null, dataScope: null },
      { segmentName: "Part C - Interest & Fees", segmentType: "BODY", position: 5, enabled: true, pageBreakBefore: true, conditionExpression: null, dataScope: null },
      { segmentName: "Part D - Repayment Schedule", segmentType: "BODY", position: 6, enabled: true, pageBreakBefore: true, conditionExpression: null, dataScope: { facilities: "facilities", base_currency: "base_currency" } },
      { segmentName: "Part E - Conditions Precedent", segmentType: "BODY", position: 7, enabled: true, pageBreakBefore: true, conditionExpression: null, dataScope: { conditions_precedent: "conditions_precedent", borrower: "borrower", effective_date: "effective_date" } },
      { segmentName: "Part F - Representations & Warranties", segmentType: "BODY", position: 8, enabled: true, pageBreakBefore: true, conditionExpression: null, dataScope: { representations: "representations", borrower: "borrower" } },
      { segmentName: "Part G - Covenants", segmentType: "BODY", position: 9, enabled: true, pageBreakBefore: true, conditionExpression: null, dataScope: { covenants: "covenants", financial_statements: "financial_statements", insurance_requirements: "insurance_requirements" } },
      { segmentName: "Part H - Security & Collateral", segmentType: "BODY", position: 10, enabled: true, pageBreakBefore: true, conditionExpression: "data.has_security === true", dataScope: { security_items: "security_items", total_security_value: "total_security_value", overall_ltv: "overall_ltv", total_facility_amount: "total_facility_amount" } },
      { segmentName: "Part I - Guarantee", segmentType: "BODY", position: 11, enabled: true, pageBreakBefore: true, conditionExpression: "data.has_guarantor === true", dataScope: { guarantors: "guarantors", borrower: "borrower", total_facility_amount: "total_facility_amount" } },
      { segmentName: "Part J - Events of Default", segmentType: "BODY", position: 12, enabled: true, pageBreakBefore: true, conditionExpression: null, dataScope: { events_of_default: "events_of_default", cross_default_threshold: "cross_default_threshold", default_interest_margin: "default_interest_margin" } },
      { segmentName: "Part K - Governing Law & Jurisdiction", segmentType: "BODY", position: 13, enabled: true, pageBreakBefore: true, conditionExpression: null, dataScope: { governing_law: "governing_law" } },
      { segmentName: "Part L - Miscellaneous", segmentType: "BODY", position: 14, enabled: true, pageBreakBefore: true, conditionExpression: null, dataScope: null },
      { segmentName: "Appendix A - Compliance Certificate", segmentType: "BODY", position: 15, enabled: true, pageBreakBefore: true, conditionExpression: null, dataScope: { covenants: "covenants", borrower: "borrower" } },
      { segmentName: "Appendix B - Drawdown Notice", segmentType: "BODY", position: 16, enabled: true, pageBreakBefore: true, conditionExpression: null, dataScope: { facilities: "facilities", borrower: "borrower", bank: "bank" } },
      { segmentName: "Appendix C - Fee Schedule", segmentType: "BODY", position: 17, enabled: true, pageBreakBefore: true, conditionExpression: null, dataScope: { fees: "fees", total_fee_amount: "total_fee_amount" } },
      { segmentName: "Signature Page", segmentType: "BODY", position: 18, enabled: true, pageBreakBefore: true, conditionExpression: null, dataScope: null, footerFileName: "signature-footer" },
    ],
  };
}

// ══════════════════════════════════════════════════════════════
// Main: Generate all docx files and pack into ZIP
// ══════════════════════════════════════════════════════════════
async function main() {
  const outDir = path.join(path.dirname(new URL(import.meta.url).pathname.replace(/^\/([A-Z]:)/, '$1')), 'output');
  fs.mkdirSync(outDir, { recursive: true });

  const segments = [
    { name: 'Cover Page', builder: buildCoverPage },
    { name: 'Table of Contents', builder: buildTableOfContents },
    { name: 'Part A - Definitions & Interpretation', builder: buildDefinitions },
    { name: 'Part B - Facility Details', builder: buildFacilityDetails },
    { name: 'Part C - Interest & Fees', builder: buildInterestFees },
    { name: 'Part D - Repayment Schedule', builder: buildRepaymentSchedule },
    { name: 'Part E - Conditions Precedent', builder: buildConditionsPrecedent },
    { name: 'Part F - Representations & Warranties', builder: buildRepresentations },
    { name: 'Part G - Covenants', builder: buildCovenants },
    { name: 'Part H - Security & Collateral', builder: buildSecurityCollateral },
    { name: 'Part I - Guarantee', builder: buildGuarantee },
    { name: 'Part J - Events of Default', builder: buildEventsOfDefault },
    { name: 'Part K - Governing Law & Jurisdiction', builder: buildGoverningLaw },
    { name: 'Part L - Miscellaneous', builder: buildMiscellaneous },
    { name: 'Appendix A - Compliance Certificate', builder: buildAppendixCompliance },
    { name: 'Appendix B - Drawdown Notice', builder: buildAppendixDrawdown },
    { name: 'Appendix C - Fee Schedule', builder: buildAppendixFeeSchedule },
    { name: 'Signature Page', builder: buildSignaturePage },
  ];

  const headers = [
    { name: 'cover-header', builder: buildCoverHeader },
    { name: 'standard-header', builder: buildStandardHeader },
  ];
  const footers = [
    { name: 'cover-footer', builder: buildCoverFooter },
    { name: 'standard-footer', builder: buildStandardFooter },
    { name: 'signature-footer', builder: buildSignatureFooter },
  ];

  const zipPath = path.join(outDir, 'fol-template-import.zip');
  const output = fs.createWriteStream(zipPath);
  const archive = archiver('zip', { zlib: { level: 9 } });

  archive.pipe(output);

  // Add config.json
  archive.append(JSON.stringify(buildConfig(), null, 2), { name: 'config.json' });

  // Add test-data.json from templates dir
  const testDataPath = path.join(path.dirname(new URL(import.meta.url).pathname.replace(/^\/([A-Z]:)/, '$1')), '..', 'templates', 'international-bank-fol', 'sample-data.json');
  if (fs.existsSync(testDataPath)) {
    archive.file(testDataPath, { name: 'test-data.json' });
  }

  // Add parameters.json
  const paramsPath = path.join(path.dirname(new URL(import.meta.url).pathname.replace(/^\/([A-Z]:)/, '$1')), '..', 'templates', 'international-bank-fol', 'parameters-flat.json');
  if (fs.existsSync(paramsPath)) {
    archive.file(paramsPath, { name: 'parameters.json' });
  }

  // Generate and add segment docx files
  for (const seg of segments) {
    console.log(`Generating segment: ${seg.name}`);
    const doc = seg.builder();
    const buffer = await Packer.toBuffer(doc);
    archive.append(buffer, { name: `segments/${seg.name}.docx` });
  }

  // Generate and add header docx files
  for (const h of headers) {
    console.log(`Generating header: ${h.name}`);
    const doc = h.builder();
    const buffer = await Packer.toBuffer(doc);
    archive.append(buffer, { name: `headers/${h.name}.docx` });
  }

  // Generate and add footer docx files
  for (const f of footers) {
    console.log(`Generating footer: ${f.name}`);
    const doc = f.builder();
    const buffer = await Packer.toBuffer(doc);
    archive.append(buffer, { name: `footers/${f.name}.docx` });
  }

  await archive.finalize();

  await new Promise((resolve) => output.on('close', resolve));
  console.log(`\n✅ ZIP created: ${zipPath} (${(fs.statSync(zipPath).size / 1024).toFixed(1)} KB)`);
  console.log(`\nTo import: POST /api/composite-templates/import with this ZIP file`);
}

main().catch(err => { console.error('❌ Error:', err); process.exit(1); });
