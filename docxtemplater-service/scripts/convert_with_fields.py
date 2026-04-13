#!/usr/bin/env python3
"""
UNO-based Word-to-PDF converter with full field refresh.

This script uses the LibreOffice UNO API to:
1. Open a .docx document in headless mode
2. Force-refresh ALL dynamic fields (page numbers, NUMPAGES, cross-references,
   date/time fields, sequence fields, etc.)
3. Force-refresh ALL document indexes (Table of Contents, alphabetical index,
   table index, illustration index, etc.)
4. Recalculate the document layout so page numbers are accurate
5. Export to PDF via the writer_pdf_Export filter
6. Close the document without saving changes to the original

Why UNO instead of --convert-to pdf?
  LibreOffice's CLI --convert-to does NOT reliably refresh dynamic fields.
  Even with UpdateDocMode=3, some field types (especially NUMPAGES, cross-refs
  to headings, and TOC page numbers) may retain stale cached values from the
  last save in Word/OnlyOffice. The UNO API gives us explicit control over
  the field update lifecycle.

Usage:
  python3 convert_with_fields.py <input.docx> <output.pdf>

Exit codes:
  0 = success
  1 = error (message printed to stderr)
"""

import sys
import os
import subprocess
import time
import signal

def file_to_url(filepath):
    """Convert a filesystem path to a file:// URL."""
    abs_path = os.path.abspath(filepath)
    # On Windows, paths need special handling, but in Docker we're always on Linux
    return 'file://' + abs_path


def find_soffice():
    """Find the soffice binary path."""
    candidates = [
        os.environ.get('LIBREOFFICE_BIN', ''),
        '/usr/bin/soffice',
        '/usr/lib/libreoffice/program/soffice',
        'soffice',
    ]
    for c in candidates:
        if c and os.path.isfile(c):
            return c
    # Fallback: assume it's on PATH
    return 'soffice'


def start_libreoffice_listener(user_installation, port=2002):
    """
    Start a LibreOffice instance listening on a UNO socket.
    Returns the subprocess.Popen object.
    """
    soffice = find_soffice()
    args = [
        soffice,
        '--headless',
        '--norestore',
        '--nologo',
        '--nofirststartwizard',
        f'--env:UserInstallation=file://{user_installation}',
        f'--accept=socket,host=localhost,port={port};urp;StarOffice.ServiceManager',
    ]
    proc = subprocess.Popen(
        args,
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
        preexec_fn=os.setsid if hasattr(os, 'setsid') else None,
    )
    return proc


def connect_to_libreoffice(port=2002, max_retries=20, retry_interval=0.5):
    """
    Connect to a running LibreOffice instance via UNO socket.
    Retries until the connection succeeds or max_retries is exhausted.
    Returns (desktop, context).
    """
    import uno
    from com.sun.star.connection import NoConnectException

    local_context = uno.getComponentContext()
    resolver = local_context.ServiceManager.createInstanceWithContext(
        'com.sun.star.bridge.UnoUrlResolver', local_context
    )

    connect_string = (
        f'uno:socket,host=localhost,port={port};urp;StarOffice.ComponentContext'
    )

    for attempt in range(max_retries):
        try:
            ctx = resolver.resolve(connect_string)
            smgr = ctx.ServiceManager
            desktop = smgr.createInstanceWithContext(
                'com.sun.star.frame.Desktop', ctx
            )
            return desktop, ctx
        except NoConnectException:
            if attempt < max_retries - 1:
                time.sleep(retry_interval)
            else:
                raise RuntimeError(
                    f'Failed to connect to LibreOffice on port {port} '
                    f'after {max_retries} attempts'
                )


def refresh_fields(doc):
    """
    Refresh all dynamic fields in the document.

    This covers:
    - Page number fields (PAGE, NUMPAGES, PAGEREF)
    - Cross-reference fields
    - Date/time fields
    - Sequence/caption number fields
    - User-defined fields
    - Database fields
    - Any other text field type
    """
    # Method 1: Enumerate and update all text fields individually
    try:
        text_fields = doc.getTextFields()
        field_enum = text_fields.createEnumeration()
        while field_enum.hasMoreElements():
            field = field_enum.nextElement()
            try:
                # update() recalculates the field value
                field.update()
            except Exception:
                pass
    except Exception as e:
        print(f'Warning: field enumeration update failed: {e}', file=sys.stderr)

    # Method 2: Use the text fields supplier to refresh via master fields
    # This catches fields that might be missed by simple enumeration
    try:
        if hasattr(doc, 'getTextFieldMasters'):
            masters = doc.getTextFieldMasters()
            master_names = masters.getElementNames()
            for name in master_names:
                try:
                    master = masters.getByName(name)
                    # Trigger dependent field updates
                    deps = master.getDependentTextFields()
                    dep_enum = deps.createEnumeration()
                    while dep_enum.hasMoreElements():
                        dep_field = dep_enum.nextElement()
                        try:
                            dep_field.update()
                        except Exception:
                            pass
                except Exception:
                    pass
    except Exception as e:
        print(f'Warning: master field update failed: {e}', file=sys.stderr)


def refresh_indexes(doc):
    """
    Refresh all document indexes: TOC, alphabetical index, table index,
    illustration index, object index, user-defined indexes, bibliography.

    After refreshing, the page numbers in the TOC will match the actual
    page layout.
    """
    try:
        indexes = doc.getDocumentIndexes()
        count = indexes.getCount()
        for i in range(count):
            try:
                index = indexes.getByIndex(i)
                index.update()
            except Exception as e:
                print(f'Warning: index {i} update failed: {e}', file=sys.stderr)
    except Exception as e:
        print(f'Warning: index refresh failed: {e}', file=sys.stderr)


def force_layout_recalculation(doc):
    """
    Force the document to recalculate its layout.

    This is critical for page number accuracy: after field updates,
    the layout engine needs to reflow the document to determine the
    correct page breaks and page counts.

    We do this by accessing the last page number through the view cursor,
    which forces a full layout pass.
    """
    try:
        # Access the document's controller to force layout
        controller = doc.getCurrentController()
        if controller is not None:
            view_cursor = controller.getViewCursor()
            # Jump to the end of the document to force full layout calculation
            view_cursor.jumpToEndOfDocument()
            # Jump back to start
            view_cursor.jumpToStartOfDocument()
    except Exception as e:
        print(f'Warning: layout recalculation via cursor failed: {e}', file=sys.stderr)

    # Alternative: dispatch UpdateAll command
    try:
        from com.sun.star.beans import PropertyValue
        controller = doc.getCurrentController()
        if controller is not None:
            frame = controller.getFrame()
            dispatcher = doc.getServiceManager() if hasattr(doc, 'getServiceManager') else None

            # Try using the dispatch helper
            import uno
            ctx = uno.getComponentContext()
            smgr = ctx.ServiceManager
            dispatch_helper = smgr.createInstanceWithContext(
                'com.sun.star.frame.DispatchHelper', ctx
            )
            # UpdateAll refreshes everything including layout-dependent fields
            dispatch_helper.executeDispatch(frame, '.uno:UpdateAll', '', 0, ())
            # UpdateFields specifically targets field codes
            dispatch_helper.executeDispatch(frame, '.uno:UpdateFields', '', 0, ())
    except Exception as e:
        print(f'Warning: dispatch UpdateAll failed: {e}', file=sys.stderr)


def export_to_pdf(doc, output_url):
    """Export the document to PDF using the writer_pdf_Export filter."""
    from com.sun.star.beans import PropertyValue

    # PDF export filter properties
    filter_data = []

    # UseLosslessCompression for better quality
    p = PropertyValue()
    p.Name = 'UseLosslessCompression'
    p.Value = False
    filter_data.append(p)

    # Quality setting (1-100)
    p = PropertyValue()
    p.Name = 'Quality'
    p.Value = 90
    filter_data.append(p)

    # Export bookmarks for TOC navigation in PDF
    p = PropertyValue()
    p.Name = 'ExportBookmarks'
    p.Value = True
    filter_data.append(p)

    # Build the main export properties
    export_props = []

    p = PropertyValue()
    p.Name = 'FilterName'
    p.Value = 'writer_pdf_Export'
    export_props.append(p)

    p = PropertyValue()
    p.Name = 'FilterData'
    p.Value = uno.Any('[]com.sun.star.beans.PropertyValue', tuple(filter_data))
    export_props.append(p)

    doc.storeToURL(output_url, tuple(export_props))


def convert(input_path, output_path):
    """
    Main conversion function.

    1. Start a temporary LibreOffice listener
    2. Connect via UNO
    3. Open the document
    4. Refresh all fields and indexes
    5. Force layout recalculation
    6. Do a second field refresh pass (catches fields that depend on layout)
    7. Export to PDF
    8. Clean up
    """
    import uno

    # Use a unique port to avoid conflicts with concurrent conversions
    port = 2002 + (os.getpid() % 1000)
    user_install = os.path.join(
        os.path.dirname(os.path.abspath(input_path)),
        f'.uno_profile_{port}'
    )
    os.makedirs(user_install, exist_ok=True)

    input_url = file_to_url(input_path)
    output_url = file_to_url(output_path)

    lo_proc = None
    doc = None

    try:
        # Start LibreOffice listener
        lo_proc = start_libreoffice_listener(user_install, port)
        print(f'Started LibreOffice listener on port {port}, PID={lo_proc.pid}',
              file=sys.stderr)

        # Connect
        desktop, ctx = connect_to_libreoffice(port)
        print('Connected to LibreOffice via UNO', file=sys.stderr)

        # Open document (hidden, with macros disabled for security)
        from com.sun.star.beans import PropertyValue

        open_props = []

        p = PropertyValue()
        p.Name = 'Hidden'
        p.Value = True
        open_props.append(p)

        p = PropertyValue()
        p.Name = 'MacroExecutionMode'
        p.Value = 0  # NEVER_EXECUTE
        open_props.append(p)

        p = PropertyValue()
        p.Name = 'UpdateDocMode'
        p.Value = 3  # FULL_UPDATE on load
        open_props.append(p)

        doc = desktop.loadComponentFromURL(
            input_url, '_blank', 0, tuple(open_props)
        )

        if doc is None:
            raise RuntimeError(f'Failed to open document: {input_path}')

        print('Document opened, refreshing fields...', file=sys.stderr)

        # === FIELD REFRESH PIPELINE ===

        # Pass 1: Refresh all text fields
        refresh_fields(doc)

        # Pass 2: Refresh all indexes (TOC, etc.)
        refresh_indexes(doc)

        # Pass 3: Force layout recalculation
        # This is critical — page numbers can only be correct after layout
        force_layout_recalculation(doc)

        # Pass 4: Second field refresh
        # After layout recalculation, NUMPAGES and page-dependent fields
        # may have changed. We need another pass to pick up these changes.
        refresh_fields(doc)

        # Pass 5: Final index refresh
        # TOC page numbers depend on the layout, so refresh again after
        # the second field pass
        refresh_indexes(doc)

        # Small delay to let LibreOffice finalize layout
        time.sleep(0.3)

        print('Fields refreshed, exporting to PDF...', file=sys.stderr)

        # Export to PDF
        export_to_pdf(doc, output_url)

        print(f'PDF exported successfully: {output_path}', file=sys.stderr)

    finally:
        # Close document
        if doc is not None:
            try:
                doc.close(True)
            except Exception:
                pass

        # Terminate LibreOffice
        if lo_proc is not None:
            try:
                if hasattr(os, 'killpg'):
                    os.killpg(os.getpgid(lo_proc.pid), signal.SIGTERM)
                else:
                    lo_proc.terminate()
                lo_proc.wait(timeout=10)
            except Exception:
                try:
                    lo_proc.kill()
                except Exception:
                    pass

        # Clean up user profile
        try:
            import shutil
            shutil.rmtree(user_install, ignore_errors=True)
        except Exception:
            pass


if __name__ == '__main__':
    if len(sys.argv) != 3:
        print(f'Usage: {sys.argv[0]} <input.docx> <output.pdf>', file=sys.stderr)
        sys.exit(1)

    input_file = sys.argv[1]
    output_file = sys.argv[2]

    if not os.path.isfile(input_file):
        print(f'Error: input file not found: {input_file}', file=sys.stderr)
        sys.exit(1)

    try:
        convert(input_file, output_file)
    except Exception as e:
        print(f'Error: {e}', file=sys.stderr)
        sys.exit(1)
