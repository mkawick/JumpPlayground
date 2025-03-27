using System.Collections;
using System.Collections.Generic;
using UnityEngine;

public class WireframeTrigger : MonoBehaviour
{
    public void ToggleAllWireframes()
    {
        var wires = GetComponentsInChildren<DrawCollider>();
        foreach (var wire in wires)
        {
            wire.ToggleOverlay();
        }
    }
}
