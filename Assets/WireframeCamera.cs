using System.Collections;
using System.Collections.Generic;
using UnityEngine;

public class WireframeCamera : MonoBehaviour
{
    private bool wireframesOn;
    public bool WireframesOn { get { return wireframesOn; } set { wireframesOn = !wireframesOn; } }

    void OnPreRender()
    {
        GL.wireframe = wireframesOn;
    }

    void OnPostRender()
    {
        GL.wireframe = false;
    }
}
