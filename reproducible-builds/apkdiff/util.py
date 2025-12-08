# Utility functions taken from https://github.com/TheTechZone/reproducible-tests/blob/d8c73772b87fbe337eb852e338238c95703d59d6/comparators/arsc_compare.py


def format_differences(diffs, indent=0):
    """Format differences in a human-readable form"""
    output = []
    indent_str = " " * indent

    for path, diff in sorted(diffs.items()):
        if isinstance(diff, dict):
            output.append(f"{indent_str}{path}:")
            output.append(format_differences(diff, indent + 2))
        elif isinstance(diff, list):
            output.append(f"{indent_str}{path}: [{', '.join(map(str, diff))}]")
        else:
            output.append(f"{indent_str}{path}: {diff}")

    return "\n".join(output)


def deep_compare(
    obj1,
    obj2,
    path="",
    max_depth=10,
    current_depth=0,
    exclude_attrs=None,
    include_callable=False,
):
    """
    Generic deep comparison of two Python objects.

    Args:
        obj1: First object to compare
        obj2: Second object to compare
        path: Current attribute path (for nested comparisons)
        max_depth: Maximum recursion depth
        current_depth: Current recursion depth
        exclude_attrs: List of attribute names to exclude from comparison
        include_callable: Whether to include callable attributes in comparison

    Returns:
        A dictionary mapping paths to differences, empty if objects are identical
    """

    if exclude_attrs is None:
        exclude_attrs = set()
    else:
        exclude_attrs = set(exclude_attrs)

    # Add common attributes to exclude
    exclude_attrs.update(["__dict__", "__weakref__", "__module__", "__doc__"])

    differences = {}

    # Check the recursion limit
    if current_depth > max_depth:
        return {f"{path} [max depth reached]": "Recursion limit reached"}

    # Basic identity/equality check
    if obj1 is obj2:  # Same object (identity)
        return {}

    if obj1 == obj2:  # Equal values
        return {}

    # Check for different types
    if type(obj1) != type(obj2):
        return {path: f"Type mismatch: {type(obj1).__name__} vs {type(obj2).__name__}"}

    # Handle None
    if obj1 is None or obj2 is None:
        return {path: f"{obj1} vs {obj2}"}

    # Handle primitive types
    if isinstance(obj1, (int, float, str, bool, bytes, complex)):
        return {path: f"{obj1} vs {obj2}"}

    # Handle sequences (list, tuple)
    if isinstance(obj1, (list, tuple)):
        if len(obj1) != len(obj2):
            differences[f"{path}.length"] = f"{len(obj1)} vs {len(obj2)}"

        # Compare elements
        for i in range(min(len(obj1), len(obj2))):
            item_path = f"{path}[{i}]"
            item_diffs = deep_compare(
                obj1[i],
                obj2[i],
                item_path,
                max_depth,
                current_depth + 1,
                exclude_attrs,
                include_callable,
            )
            differences.update(item_diffs)

        # Report extra elements
        if len(obj1) > len(obj2):
            for i in range(len(obj2), len(obj1)):
                differences[f"{path}[{i}]"] = f"{obj1[i]} vs [missing]"
        elif len(obj2) > len(obj1):
            for i in range(len(obj1), len(obj2)):
                differences[f"{path}[{i}]"] = f"[missing] vs {obj2[i]}"

        return differences

    # Handle dictionaries
    if isinstance(obj1, dict):
        keys1 = set(obj1.keys())
        keys2 = set(obj2.keys())

        # Check for different keys
        if keys1 != keys2:
            only_in_1 = keys1 - keys2
            only_in_2 = keys2 - keys1
            if only_in_1:
                differences[f"{path}.keys_only_in_first"] = sorted(only_in_1)
            if only_in_2:
                differences[f"{path}.keys_only_in_second"] = sorted(only_in_2)

        # Compare common keys
        for key in keys1 & keys2:
            key_path = f"{path}[{repr(key)}]"
            key_diffs = deep_compare(
                obj1[key],
                obj2[key],
                key_path,
                max_depth,
                current_depth + 1,
                exclude_attrs,
                include_callable,
            )
            differences.update(key_diffs)

        return differences

    # Handle sets
    if isinstance(obj1, set):
        only_in_1 = obj1 - obj2
        only_in_2 = obj2 - obj1

        if only_in_1:
            differences[f"{path}.items_only_in_first"] = sorted(only_in_1)
        if only_in_2:
            differences[f"{path}.items_only_in_second"] = sorted(only_in_2)

        return differences

    # Handle custom objects and classes
    try:
        # Try to get all attributes
        attrs1 = dir(obj1)

        # Filter attributes
        filtered_attrs = [attr for attr in attrs1 if not attr.startswith("__") and attr not in exclude_attrs and (include_callable or not callable(getattr(obj1, attr, None)))]

        # Compare each attribute
        for attr in filtered_attrs:
            try:
                # Skip unintended attributes
                if attr in exclude_attrs:
                    continue

                # Get attribute values
                val1 = getattr(obj1, attr)

                # Skip callables unless explicitly included
                if callable(val1) and not include_callable:
                    continue

                # Check if attr exists in obj2
                if not hasattr(obj2, attr):
                    differences[f"{path}.{attr}"] = f"{val1} vs [attribute missing]"
                    continue

                val2 = getattr(obj2, attr)

                # Compare values
                attr_path = f"{path}.{attr}"
                attr_diffs = deep_compare(
                    val1,
                    val2,
                    attr_path,
                    max_depth,
                    current_depth + 1,
                    exclude_attrs,
                    include_callable,
                )
                differences.update(attr_diffs)
            except Exception as e:
                differences[f"{path}.{attr}"] = f"Error comparing: {str(e)}"

    except Exception as e:
        differences[path] = f"Error accessing attributes: {str(e)}"

    return differences
