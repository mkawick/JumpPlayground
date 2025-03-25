using System;
using System.Collections;
using System.Collections.Generic;
using System.Text;

namespace TinyWizard.Utilities
{
    public static class StringUtil {

        /// <summary>Joins a collection together into a string by calling ToString() on each element.</summary>
        public static string JoinToString(this IEnumerable values, string separator) {
            if (values == null) {
                return null;
            }
            var builder = new StringBuilder();
            bool first = true;
            foreach (var item in values) {
                if (!first) {
                    builder.Append(separator);
                }
                first = false;
                builder.Append(item + "");
            }
            return builder.ToString();
        }

        /// <summary>Joins a collection together into a string by calling a function on each element.</summary>
        public static string JoinToString<T>(this IEnumerable<T> values, string separator, Func<T, string> eachItem) {
            if(values == null) {
                return null;
            }
            var builder = new StringBuilder();
            bool first = true;
            foreach (var item in values) {
                if (!first) {
                    builder.Append(separator);
                }
                first = false;
                builder.Append(eachItem(item));
            }
            return builder.ToString();
        }

        /// <summary>Returns true if the provided string is null or empty.</summary>
        public static bool IsNullOrEmpty(this string value) {
            return string.IsNullOrEmpty(value);
        }

        /// <summary>Returns true if the provided string is null, empty, or whitespace.</summary>
        public static bool IsNullOrWhitespace(this string value) {
            return string.IsNullOrWhiteSpace(value);
        }

        /// <summary>Returns true if the current string contains <paramref name="value"/>.</summary>
        public static bool ContainsIgnoreCase(this string current, string value) {
            if (current == null || value == null) {
                return false;
            }
            return current.IndexOf(value, StringComparison.OrdinalIgnoreCase) >= 0;
        }

        /// <summary>Attempts to parse the string into the specified type. Returns true if successful.</summary>
        public static bool TryParse<T>(this string value, out T parsed) where T : struct, IConvertible {
            parsed = default;
            if (value.IsNullOrEmpty()) {
                return false;
            }

            try {
                parsed = (T)Convert.ChangeType(value, typeof(T));
                return true;
            } catch { }

            return false;
        }

        /// <summary>Attempts to parse the string into the specified type. Returns the <paramref name="defaultValue"/> if unsuccessful.</summary>
        public static T Parse<T>(this string value, T defaultValue = default) where T : struct, IConvertible {
            if (!value.TryParse(out T parsed)) {
                return defaultValue;
            }
            return parsed;
        }

        /// <summary>Splits the string into sub strings and returns each entry individually 
        /// without creating an array or list.</summary>
        public static IEnumerable<string> SplitEnumerable(this string value, string delimiter = " ",
            StringSplitOptions options = StringSplitOptions.RemoveEmptyEntries) {

            if (string.IsNullOrEmpty(value)) {
                yield break;
            }
            int index = 0;
            while (index < value.Length) {
                int splitIndex = value.IndexOf(delimiter, index);
                if (splitIndex < 0) {
                    if (index == 0) {
                        yield return value;
                    } else {
                        yield return value.Substring(index, value.Length - index);
                    }
                    yield break;
                } else {
                    if (splitIndex == index) {
                        if (!options.HasFlag(StringSplitOptions.RemoveEmptyEntries)) {
                            yield return string.Empty;
                        }
                        index += delimiter.Length;
                        continue;
                    }
                    yield return value.Substring(index, splitIndex - index);
                    index = splitIndex + delimiter.Length;
                    if (splitIndex == value.Length - delimiter.Length) {
                        if (!options.HasFlag(StringSplitOptions.RemoveEmptyEntries)) {
                            yield return string.Empty;
                        }
                    }
                }
            }
        }
        
        public static bool HasChars(this string self) => !string.IsNullOrEmpty(self);

        public static string Reverse(this string s) {
            char[] charArray = s.ToCharArray();
            Array.Reverse(charArray);
            return new string(charArray);
        }

        /// <summary>Returns true if the strings are equal while ignoring case.</summary>
        public static bool EqualsIgnoreCase(this string value, string other) {
            return StringComparer.OrdinalIgnoreCase.Equals(value, other);
        }
    }
}
