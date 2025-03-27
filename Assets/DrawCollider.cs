using System.Collections;
using System.Collections.Generic;
using UnityEngine;

public class DrawCollider : MonoBehaviour
{
    bool showingOverlay;
    Shader originalShader;
    // Start is called before the first frame update
    void Start()
    {
        var renderer = GetComponentInChildren<MeshRenderer>();
        originalShader = renderer.materials[0].shader; //Shader.Find("Universal Render Pipeline/Lit");
    }

    public void ToggleOverlay()
    {
        Shader shader;
        showingOverlay = !showingOverlay;
        if (showingOverlay)
        {
            shader = Shader.Find("Unlit/WireframeSimple");
        }
        else
        {
            shader = originalShader;
        }

        var renderers = GetComponentsInChildren<MeshRenderer>();

        foreach (var renderer in renderers)
        {
            foreach (Material material in renderer.materials)
            {
                material.shader = shader;
            }            
        }
    }
}
