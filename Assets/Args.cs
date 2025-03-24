using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;

namespace TinyWizard.Utilities {

    /// <summary>Parses the given line into key value pairs. You can then use various functions to retrive them. 
    /// Any word starting with a hyphen (-), forward slash (/), or backward slash (\) will be counted as the key. 
    /// Keys cannot contain spaces. The value begins when there is a space after the key. The value portion is optional 
    /// and can be left blank. All keys and values need to be separated by spaces. Values will contain all text up until 
    /// the next key starts. The leading and tailing whitespace is removed from values.</summary>
    public struct Args {

        /// <summary>The entire unseparated command line string. See <see cref="Args"/> for formatting.</summary>
        public string Line;

        /// <summary>The command line separated by spaces. See <see cref="Args"/> for formatting.</summary>
        public string[] Arguments;

        /// <summary>A dictionary of key-value pairs from the command line. See <see cref="Args"/> for formatting.</summary>
        public Dictionary<string, string> Params;

        /// <summary>A dictionary of key-value pairs from the command line. This returns only the key-value pairs that 
        /// have not been marked by the <see cref="Use(string)"/> function. See <see cref="Args"/> for formatting.</summary>
        public Dictionary<string, string> UnusedParams;

        /// <summary>A collection of the prefixes for keys so that the original command line arguments can be put back 
        /// together using <see cref="GetUnusedLine"/>.</summary>
        public Dictionary<string, string> KeyPrefixes;

        /// <summary>
        /// Parses the given line into key value pairs. You can then use various functions to retrive them. Or you can add your own.
        /// </summary>
        public Args(string line) {
            Line = line;
            Arguments = line.Split(new[] { ' ', '\t' }, StringSplitOptions.RemoveEmptyEntries);
            Params = new Dictionary<string, string>(StringComparer.OrdinalIgnoreCase);
            UnusedParams = new Dictionary<string, string>(StringComparer.OrdinalIgnoreCase);
            KeyPrefixes = new Dictionary<string, string>(StringComparer.OrdinalIgnoreCase);

            var builder = new StringBuilder();
            for (int i = 0; i < Arguments.Length; i++) {
                string arg = Arguments[i];
                if (IsAKey(arg)) {
                    builder.Length = 0;
                    while (i + 1 < Arguments.Length) {
                        if (IsAKey(Arguments[i + 1]))
                            break;

                        i++;
                        builder.AppendFormat(" {0}", Arguments[i]);
                    }
                    var key = CleanKey(arg);
                    var prefix = arg.Substring(0, arg.IndexOf(key));
                    KeyPrefixes.Add(key, prefix);
                    Params.Add(key, builder.ToString().Trim());
                    UnusedParams.Add(key, builder.ToString().Trim());
                }
            }
        }

        /// <summary>Processes the given args and determines key value pairs. You can then use various functions to retrive them. Or you can add your own.</summary>
        public Args(string[] args) : this(args.JoinToString(" ")) { }

        /// <summary>Determines if the provided key is actually formatted like a key. See <see cref="Args"/> 
        /// for how keys can be formatted.</summary>
        public bool IsAKey(string key) {
            return key.StartsWith("-");
        }

        /// <summary>Removes any decorators from the provided key to return the key's value.</summary>
        public string CleanKey(string key) {
            return key.Trim('-');
        }

        /// <summary>Gets the full key with the decorators it had.</summary>
        public string GetFullKey(string key) {
            key = CleanKey(key);
            KeyPrefixes.TryGetValue(key, out var value);
            return value;
        }

        /// <summary>Adds a line full or args to these args. You can then use it normally such as calling 
        /// <see cref="Get(string)"/> or <see cref="Use(string)"/> to get the value.</summary>
        public void AddLine(string line) {
            if (line.IsNullOrEmpty()) {
                return;
            }
            var args = new Args(line);
            AddArgs(args);
        }

        /// <summary>Adds args to these args. You can then use it normally such as calling 
        /// <see cref="Get(string)"/> or <see cref="Use(string)"/> to get the value.</summary>
        public void AddArgs(Args args) {
            if (args.Params.IsNullOrEmpty()) {
                return;
            }
            foreach (var entry in args.Params) {
                Add(entry.Key, entry.Value);
            }
        }

        /// <summary>Adds a key value pair to this args. You can then use it normally such as calling 
        /// <see cref="Get(string)"/> or <see cref="Use(string)"/> to get the value.</summary>
        public void Add(string key, string value = null) {
            var cleaned = CleanKey(key);
            var prefix = key.Substring(0, key.IndexOf(cleaned));
            KeyPrefixes[key] = prefix.IsNullOrEmpty() ? "-" : prefix;
            Params[cleaned] = value ?? string.Empty;
            UnusedParams[cleaned] = value ?? string.Empty;
            Line = Params.Select(kv => !kv.Value.IsNullOrEmpty() ? $"-{kv.Key} {kv.Value}" : $"-{kv.Key}").JoinToString(" ");
        }

        /// <summary>Determines if any of the keys provided exist in the args.</summary>
        public bool Has(params string[] keys) {
            foreach (var key in keys) {
                if (Params.ContainsKey(CleanKey(key)))
                    return true;
            }
            return false;
        }

        /// <summary>Gets a string value by the provided key.</summary>
        public string Get(string key, string defaultValue = default) {
            key = CleanKey(key);
            if (!Params.TryGetValue(key, out var value)) {
                return defaultValue;
            }
            return value;
        }

        /// <summary>Gets a string value using the first matching key in the provided array of keys.</summary>
        public string Get(params string[] keys) {
            for (int i = 0; i < keys.Length; i++) {
                if (TryGet(keys[i], out var value))
                    return value;
            }
            return default;
        }

        /// <summary>Gets a convertible value using the provided key. Convertible types include bool, byte, 
        /// short, int, long, float, double, char, string, and any other struct that implements the IConvertible 
        /// interface.</summary>
        public T Get<T>(string key, T defaultValue = default) where T : struct, IConvertible {
            key = CleanKey(key);
            if (!Params.TryGetValue(key, out var value)) {
                return defaultValue;
            }
            try {
                var type = typeof(T);
                if (type.IsEnum) {
                    return (T)Enum.Parse(type, value, true);
                }
                return (T)Convert.ChangeType(value, type);
            } catch { }
            return default;
        }

        /// <summary>Gets a convertible value using the first matching key in the provided keys. Convertible types 
        /// include bool, byte, short, int, long, float, double, char, string, and any other struct that implements the 
        /// IConvertible interface.</summary>
        public T Get<T>(params string[] keys) where T : struct, IConvertible {
            for (int i = 0; i < keys.Length; i++) {
                if (TryGet<T>(keys[i], out var value))
                    return value;
            }
            return default;
        }

        /// <summary>Tries to get a string value by the provided key. Returns false if unable to.</summary>
        public bool TryGet(string key, out string value) {
            key = CleanKey(key);
            return Params.TryGetValue(key, out value);
        }

        /// <summary>Tries to get a string value using the first matching key in the provided keys.</summary>
        public bool TryGet(string key1, string key2, out string value) {
            return TryGet(key1, out value)
                || TryGet(key2, out value);
        }

        /// <summary>Tries to get a string value using the first matching key in the provided keys.</summary>
        public bool TryGet(string key1, string key2, string key3, out string value) {
            return TryGet(key1, out value)
                || TryGet(key2, out value)
                || TryGet(key3, out value);
        }

        /// <summary>Tries to get a string value using the first matching key in the provided keys.</summary>
        public bool TryGet(string key1, string key2, string key3, string key4, out string value) {
            return TryGet(key1, out value)
                || TryGet(key2, out value)
                || TryGet(key3, out value)
                || TryGet(key4, out value);
        }

        /// <summary>Tries to get a convertible value using the provided key. Convertible types include bool, byte, 
        /// short, int, long, float, double, char, string, and any other struct that implements the IConvertible 
        /// interface.</summary>
        public bool TryGet<T>(string key, out T value) where T : struct, IConvertible {
            key = CleanKey(key);
            value = default;
            if (!Params.TryGetValue(key, out var stringValue))
                return false;
            try {
                var type = typeof(T);
                if (type.IsEnum) {
                    value = (T)Enum.Parse(type, stringValue, true);
                    return true;
                }
                value = (T)Convert.ChangeType(stringValue, type);
                return true;
            } catch { }
            return false;
        }

        /// <summary>Tries to get a convertible value using the first matching key in the provided keys. Convertible types 
        /// include bool, byte, short, int, long, float, double, char, string, and any other struct that implements the 
        /// IConvertible interface.</summary>
        public bool TryGet<T>(string key1, string key2, out T value) where T : struct, IConvertible {
            return TryGet(key1, out value)
                || TryGet(key2, out value);
        }

        /// <summary>Tries to get a convertible value using the first matching key in the provided keys. Convertible types 
        /// include bool, byte, short, int, long, float, double, char, string, and any other struct that implements the 
        /// IConvertible interface.</summary>
        public bool TryGet<T>(string key1, string key2, string key3, out T value) where T : struct, IConvertible {
            return TryGet(key1, out value)
                || TryGet(key2, out value)
                || TryGet(key3, out value);
        }

        /// <summary>Tries to get a convertible value using the first matching key in the provided keys. Convertible types 
        /// include bool, byte, short, int, long, float, double, char, string, and any other struct that implements the 
        /// IConvertible interface.</summary>
        public bool TryGet<T>(string key1, string key2, string key3, string key4, out T value) where T : struct, IConvertible {
            return TryGet(key1, out value)
                || TryGet(key2, out value)
                || TryGet(key3, out value)
                || TryGet(key4, out value);
        }

        /// <summary>Combines all key-value pairs that have not been retrieved using <see cref="Use(string)"/> into a 
        /// single string. Separating using spaces.</summary>
        public string GetUnusedLine() {
            var prefixes = KeyPrefixes;
            return UnusedParams.JoinToString(" ", kv => {
                if (kv.Value.IsNullOrEmpty())
                    return $"{prefixes[kv.Key]}{kv.Key}";
                return $"{prefixes[kv.Key]}{kv.Key} {kv.Value}";
            });
        }

        /// <summary>Combines all key-value pairs that have not been retrieved using <see cref="Use(string)"/> into a 
        /// single string. Separating using spaces.</summary>
        public string[] GetUnusedArgs() {
            var prefixes = KeyPrefixes;
            var args = new List<string>();
            foreach (var kv in UnusedParams) {
                args.Add(prefixes[kv.Key] + kv.Key);
                args.Add(kv.Value);
            }
            return args.ToArray();
        }

        /// <summary>Determines if any of the provided keys can be used in the <see cref="Use(string)"/> function. 
        /// Once called by the <see cref="Use(string)"/> function it is marked as used and cannot be used again.</summary>
        public bool CanUse(params string[] keys) {
            foreach (var key in keys) {
                if (UnusedParams.ContainsKey(CleanKey(key)))
                    return true;
            }
            return false;
        }

        /// <summary>Gets a string value by the provided key. Once called by this function it is marked as used and 
        /// cannot be used again.</summary>
        public string Use(string key, string defaultValue = default) {
            key = CleanKey(key);
            if (!UnusedParams.TryGetValue(key, out var value)) {
                return defaultValue;
            }
            UnusedParams.Remove(key);
            return value;
        }

        /// <summary>Gets a string value using the first matching key in the provided array of keys. Once called by this 
        /// function it is marked as used and cannot be used again.</summary>
        public string Use(params string[] keys) {
            for (int i = 0; i < keys.Length; i++) {
                if (TryUse(keys[i], out var value))
                    return value;
            }
            return default;
        }

        /// <summary>Gets a convertible value using the provided key. Convertible types include bool, byte, 
        /// short, int, long, float, double, char, string, and any other struct that implements the IConvertible 
        /// interface. Once called by this function it is marked as used and cannot be used again.</summary>
        public T Use<T>(string key, T defaultValue = default) where T : struct, IConvertible {
            key = CleanKey(key);
            if (!UnusedParams.TryGetValue(key, out var value)) {
                return defaultValue;
            }
            UnusedParams.Remove(key);
            try {
                var type = typeof(T);
                if (type.IsEnum) {
                    return (T)Enum.Parse(type, value, true);
                }
                return (T)Convert.ChangeType(value, type);
            } catch { }
            return default;
        }

        /// <summary>Gets a convertible value using the first matching key in the provided keys. Convertible types 
        /// include bool, byte, short, int, long, float, double, char, string, and any other struct that implements the 
        /// IConvertible interface. Once called by this function it is marked as used and cannot be used again.</summary>
        public T Use<T>(params string[] keys) where T : struct, IConvertible {
            for (int i = 0; i < keys.Length; i++) {
                if (TryUse<T>(keys[i], out var value))
                    return value;
            }
            return default;
        }

        /// <summary>Tries to get a string value by the provided key. Returns false if unable to. Once called by this 
        /// function it is marked as used and cannot be used again.</summary>
        public bool TryUse(string key, out string value) {
            key = CleanKey(key);
            if (UnusedParams.TryGetValue(key, out value)) {
                UnusedParams.Remove(key);
                return true;
            }
            return false;
        }

        /// <summary>Tries to get a string value using the first matching key in the provided keys. Once called by this 
        /// function it is marked as used and cannot be used again.</summary>
        public bool TryUse(string key1, string key2, out string value) {
            return TryUse(key1, out value)
                || TryUse(key2, out value);
        }

        /// <summary>Tries to get a string value using the first matching key in the provided keys. Once called by this 
        /// function it is marked as used and cannot be used again.</summary>
        public bool TryUse(string key1, string key2, string key3, out string value) {
            return TryUse(key1, out value)
                || TryUse(key2, out value)
                || TryUse(key3, out value);
        }

        /// <summary>Tries to get a string value using the first matching key in the provided keys. Once called by this 
        /// function it is marked as used and cannot be used again.</summary>
        public bool TryUse(string key1, string key2, string key3, string key4, out string value) {
            return TryUse(key1, out value)
                || TryUse(key2, out value)
                || TryUse(key3, out value)
                || TryUse(key4, out value);
        }

        /// <summary>Tries to get a convertible value using the provided key. Convertible types include bool, byte, 
        /// short, int, long, float, double, char, string, and any other struct that implements the IConvertible 
        /// interface. Once called by this function it is marked as used and cannot be used again.</summary>
        public bool TryUse<T>(string key, out T value) where T : struct, IConvertible {
            key = CleanKey(key);
            value = default;
            if (!UnusedParams.TryGetValue(key, out var stringValue))
                return false;
            UnusedParams.Remove(key);
            try {
                var type = typeof(T);
                if (type.IsEnum) {
                    value = (T)Enum.Parse(type, stringValue, true);
                    return true;
                }
                value = (T)Convert.ChangeType(stringValue, type);
                return true;
            } catch { }
            return false;
        }

        /// <summary>Tries to get a convertible value using the first matching key in the provided keys. Convertible types 
        /// include bool, byte, short, int, long, float, double, char, string, and any other struct that implements the 
        /// IConvertible interface. Once called by this function it is marked as used and cannot be used again.</summary>
        public bool TryUse<T>(string key1, string key2, out T value) where T : struct, IConvertible {
            return TryUse(key1, out value)
                || TryUse(key2, out value);
        }

        /// <summary>Tries to get a convertible value using the first matching key in the provided keys. Convertible types 
        /// include bool, byte, short, int, long, float, double, char, string, and any other struct that implements the 
        /// IConvertible interface. Once called by this function it is marked as used and cannot be used again.</summary>
        public bool TryUse<T>(string key1, string key2, string key3, out T value) where T : struct, IConvertible {
            return TryUse(key1, out value)
                || TryUse(key2, out value)
                || TryUse(key3, out value);
        }

        /// <summary>Tries to get a convertible value using the first matching key in the provided keys. Convertible types 
        /// include bool, byte, short, int, long, float, double, char, string, and any other struct that implements the 
        /// IConvertible interface. Once called by this function it is marked as used and cannot be used again.</summary>
        public bool TryUse<T>(string key1, string key2, string key3, string key4, out T value) where T : struct, IConvertible {
            return TryUse(key1, out value)
                || TryUse(key2, out value)
                || TryUse(key3, out value)
                || TryUse(key4, out value);
        }

        /// <summary>Tries to get a string value by the provided key. 
        /// This throws an exception if it cannot get the value.</summary>
        public string Require(string key, string error = default) {
            if (!TryGet(key, out string value)) {
                throw new Exception(error);
            }
            return value;
        }

        /// <summary>Tries to get a string value using the first matching key in the provided keys. 
        /// This throws an exception if it cannot get the value.</summary>
        public string Require(string key1, string key2, string error = default) {
            if (!TryGet(key1, out string value) && !TryGet(key2, out value)) {
                throw new Exception(error);
            }
            return value;
        }

        /// <summary>Tries to get a string value using the first matching key in the provided keys. 
        /// This throws an exception if it cannot get the value.</summary>
        public string Require(string key1, string key2, string key3, string error = default) {
            if (!TryGet(key1, out string value) && !TryGet(key2, out value) && !TryGet(key3, out value)) {
                throw new Exception(error);
            }
            return value;
        }

        /// <summary>Tries to get a string value using the first matching key in the provided keys. 
        /// This throws an exception if it cannot get the value.</summary>
        public string Require(string key1, string key2, string key3, string key4, string error = default) {
            if (!TryGet(key1, out string value) && !TryGet(key2, out value) && !TryGet(key3, out value) && !TryGet(key4, out value)) {
                throw new Exception(error);
            }
            return value;
        }

        /// <summary>Tries to get a convertible value using the provided key. Convertible types include bool, byte, 
        /// short, int, long, float, double, char, string, and any other struct that implements the IConvertible 
        /// interface. This throws an exception if it cannot get the value.</summary>
        public T Require<T>(string key, string error = default) where T : struct, IConvertible {
            if (!TryGet(key, out T value)) {
                throw new Exception(error);
            }
            return value;
        }

        /// <summary>Tries to get a convertible value using the first matching key in the provided keys. Convertible types 
        /// include bool, byte, short, int, long, float, double, char, string, and any other struct that implements the 
        /// IConvertible interface. This throws an exception if it cannot get the value.</summary>
        public T Require<T>(string key1, string key2, string error = default) where T : struct, IConvertible {
            if (!TryGet(key1, out T value) && !TryGet(key2, out value)) {
                throw new Exception(error);
            }
            return value;
        }

        /// <summary>Tries to get a convertible value using the first matching key in the provided keys. Convertible types 
        /// include bool, byte, short, int, long, float, double, char, string, and any other struct that implements the 
        /// IConvertible interface. This throws an exception if it cannot get the value.</summary>
        public T Require<T>(string key1, string key2, string key3, string error = default) where T : struct, IConvertible {
            if (!TryGet(key1, out T value) && !TryGet(key2, out value) && !TryGet(key3, out value)) {
                throw new Exception(error);
            }
            return value;
        }

        /// <summary>Tries to get a convertible value using the first matching key in the provided keys. Convertible types 
        /// include bool, byte, short, int, long, float, double, char, string, and any other struct that implements the 
        /// IConvertible interface. This throws an exception if it cannot get the value.</summary>
        public T Require<T>(string key1, string key2, string key3, string key4, string error = default) where T : struct, IConvertible {
            if (!TryGet(key1, out T value) && !TryGet(key2, out value) && !TryGet(key3, out value) && !TryGet(key4, out value)) {
                throw new Exception(error);
            }
            return value;
        }

        /// <summary>Tries to get a string value by the provided key. 
        /// This throws an exception if it cannot get the value.</summary>
        public string RequireUse(string key, string error = default) {
            if (!TryUse(key, out string value)) {
                throw new Exception(error);
            }
            return value;
        }

        /// <summary>Tries to get a string value using the first matching key in the provided keys. 
        /// This throws an exception if it cannot get the value.</summary>
        public string RequireUse(string key1, string key2, string error = default) {
            if (!TryUse(key1, out string value) && !TryUse(key2, out value)) {
                throw new Exception(error);
            }
            return value;
        }

        /// <summary>Tries to get a string value using the first matching key in the provided keys. 
        /// This throws an exception if it cannot get the value.</summary>
        public string RequireUse(string key1, string key2, string key3, string error = default) {
            if (!TryUse(key1, out string value) && !TryUse(key2, out value) && !TryUse(key3, out value)) {
                throw new Exception(error);
            }
            return value;
        }

        /// <summary>Tries to get a string value using the first matching key in the provided keys. 
        /// This throws an exception if it cannot get the value.</summary>
        public string RequireUse(string key1, string key2, string key3, string key4, string error = default) {
            if (!TryUse(key1, out string value) && !TryUse(key2, out value) && !TryUse(key3, out value) && !TryUse(key4, out value)) {
                throw new Exception(error);
            }
            return value;
        }

        /// <summary>Tries to get a convertible value using the provided key. Convertible types include bool, byte, 
        /// short, int, long, float, double, char, string, and any other struct that implements the IConvertible 
        /// interface. This throws an exception if it cannot get the value.</summary>
        public T RequireUse<T>(string key, string error = default) where T : struct, IConvertible {
            if (!TryUse(key, out T value)) {
                throw new Exception(error);
            }
            return value;
        }

        /// <summary>Tries to get a convertible value using the first matching key in the provided keys. Convertible types 
        /// include bool, byte, short, int, long, float, double, char, string, and any other struct that implements the 
        /// IConvertible interface. This throws an exception if it cannot get the value.</summary>
        public T RequireUse<T>(string key1, string key2, string error = default) where T : struct, IConvertible {
            if (!TryUse(key1, out T value) && !TryUse(key2, out value)) {
                throw new Exception(error);
            }
            return value;
        }

        /// <summary>Tries to get a convertible value using the first matching key in the provided keys. Convertible types 
        /// include bool, byte, short, int, long, float, double, char, string, and any other struct that implements the 
        /// IConvertible interface. This throws an exception if it cannot get the value.</summary>
        public T RequireUse<T>(string key1, string key2, string key3, string error = default) where T : struct, IConvertible {
            if (!TryUse(key1, out T value) && !TryUse(key2, out value) && !TryUse(key3, out value)) {
                throw new Exception(error);
            }
            return value;
        }

        /// <summary>Tries to get a convertible value using the first matching key in the provided keys. Convertible types 
        /// include bool, byte, short, int, long, float, double, char, string, and any other struct that implements the 
        /// IConvertible interface. This throws an exception if it cannot get the value.</summary>
        public T RequireUse<T>(string key1, string key2, string key3, string key4, string error = default) where T : struct, IConvertible {
            if (!TryUse(key1, out T value) && !TryUse(key2, out value) && !TryUse(key3, out value) && !TryUse(key4, out value)) {
                throw new Exception(error);
            }
            return value;
        }
    }
}
